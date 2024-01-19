package funcify.feature.materializer.model

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.none
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistryCreator
import funcify.feature.schema.limit.ModelLimits
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.SDLDefinition
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.GraphQLUnmodifiedType
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger
import java.util.*

internal object DomainSpecifiedDataElementSourceCreator :
    (FeatureEngineeringModel, GraphQLSchema) -> Iterable<DomainSpecifiedDataElementSource> {

    private const val TYPE_NAME: String = "domain_specified_data_element_source_creator"
    private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    private val logger: Logger = loggerFor<DomainSpecifiedDataElementSourceCreator>()

    override fun invoke(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): Iterable<DomainSpecifiedDataElementSource> {
        logger.info("{}.invoke: [ ]", TYPE_NAME)
        return graphQLSchema.queryType
            .toOption()
            .flatMap { got: GraphQLObjectType ->
                when {
                    got.name == featureEngineeringModel.dataElementFieldCoordinates.typeName -> {
                        got.getFieldDefinition(
                                featureEngineeringModel.dataElementFieldCoordinates.fieldName
                            )
                            .toOption()
                            .map { gfd: GraphQLFieldDefinition ->
                                GQLOperationPath.getRootPath().transform {
                                    appendField(gfd.name)
                                } to gfd
                            }
                    }
                    else -> {
                        none()
                    }
                }
            }
            .flatMap { (p: GQLOperationPath, fd: GraphQLFieldDefinition) ->
                GraphQLTypeUtil.unwrapAll(fd.type)
                    .toOption()
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .map { gfc: GraphQLFieldsContainer -> p to gfc }
            }
            .fold(::emptySequence) { (p: GQLOperationPath, gfc: GraphQLFieldsContainer) ->
                val desByFieldDefName: ImmutableMap<String, DataElementSource> =
                    extractDataElementSourceByFieldDefinitionNameMapFromFeatureEngineeringModel(
                        featureEngineeringModel
                    )
                gfc.fieldDefinitions
                    .asSequence()
                    .map { fd: GraphQLFieldDefinition ->
                        desByFieldDefName.getOrNone(fd.name).map { des: DataElementSource ->
                            fd to des
                        }
                    }
                    .flatMapOptions()
                    .map { (fd: GraphQLFieldDefinition, des: DataElementSource) ->
                        val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>> =
                            LinkedList()
                        traverseFieldDefinitionsUnderDomainAddingArgumentsToBuilder(
                                graphQLSchema,
                                p,
                                gfc,
                                fd,
                                des,
                                backRefQueue
                            )
                            .successIfDefined {
                                ServiceError.of(
                                    "builder instance failed to be passed back from visitor"
                                )
                            }
                            .map(
                                gatherArgumentsUntilMaximumOperationDepth(
                                    featureEngineeringModel.modelLimits,
                                    graphQLSchema,
                                    backRefQueue
                                )
                            )
                            .flatMap { b: DomainSpecifiedDataElementSource.Builder ->
                                LastUpdatedCoordinatesRegistryCreator
                                    .createLastUpdatedCoordinatesRegistryFor(
                                        featureEngineeringModel.modelLimits,
                                        graphQLSchema,
                                        p.transform { appendField(fd.name) },
                                        FieldCoordinates.coordinates(gfc.name, fd.name)
                                    )
                                    .map { lucr: LastUpdatedCoordinatesRegistry ->
                                        b.lastUpdatedCoordinatesRegistry(lucr).build()
                                    }
                            }
                            .peekIfFailure { t: Throwable ->
                                logger.warn(
                                    "{}.invoke: [ status: error occurred ][ type: {}, message: {} ]",
                                    TYPE_NAME,
                                    t::class.simpleName,
                                    t.message
                                )
                            }
                            .orElseThrow()
                    }
            }
            .asIterable()
    }

    private fun extractDataElementSourceByFieldDefinitionNameMapFromFeatureEngineeringModel(
        featureEngineeringModel: FeatureEngineeringModel
    ): ImmutableMap<String, DataElementSource> {
        return featureEngineeringModel.dataElementSourcesByName.values
            .asSequence()
            .flatMap { des: DataElementSource ->
                des.sourceSDLDefinitions
                    .asSequence()
                    .firstOrNone { sd: SDLDefinition<*> ->
                        sd is ObjectTypeDefinition && QUERY_OBJECT_TYPE_NAME == sd.name
                    }
                    .filterIsInstance<ObjectTypeDefinition>()
                    .map(ObjectTypeDefinition::getFieldDefinitions)
                    .fold(::emptyList, ::identity)
                    .asSequence()
                    .map { fd: FieldDefinition -> fd.name to des }
            }
            .reducePairsToPersistentMap()
    }

    private fun traverseFieldDefinitionsUnderDomainAddingArgumentsToBuilder(
        graphQLSchema: GraphQLSchema,
        parentPath: GQLOperationPath,
        parentFieldsContainer: GraphQLFieldsContainer,
        domainDataElementFieldDefinition: GraphQLFieldDefinition,
        dataElementSourceForDomainField: DataElementSource,
        backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>,
    ): Option<DomainSpecifiedDataElementSource.Builder> {
        return Traverser.breadthFirst(
                schemaElementTraversalFunction(graphQLSchema),
                parentPath,
                DefaultDomainSpecifiedDataElementSource.builder()
                    .graphQLSchema(graphQLSchema)
                    .domainFieldCoordinates(
                        FieldCoordinates.coordinates(
                            parentFieldsContainer.name,
                            domainDataElementFieldDefinition.name
                        )
                    )
                    .domainFieldDefinition(domainDataElementFieldDefinition)
                    .domainPath(
                        parentPath.transform { appendField(domainDataElementFieldDefinition.name) }
                    )
                    .dataElementSource(dataElementSourceForDomainField)
            )
            .traverse(
                domainDataElementFieldDefinition,
                SchemaElementTraverserVisitor(
                    graphQLTypeVisitor = DomainSpecifiedDataElementSourceVisitor(backRefQueue)
                )
            )
            .toOption()
            .mapNotNull(TraverserResult::getAccumulatedResult)
            .filterIsInstance<DomainSpecifiedDataElementSource.Builder>()
    }

    private fun schemaElementTraversalFunction(
        gs: GraphQLSchema
    ): (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLInterfaceType -> {
                    gs.getImplementations(e)
                }
                is GraphQLObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLFieldDefinition -> {
                    when (val gut: GraphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(e.type)) {
                        is GraphQLFieldsContainer -> {
                            listOf(gut)
                        }
                        else -> {
                            emptyList()
                        }
                    }
                }
                else -> {
                    emptyList()
                }
            }
        }
    }

    private fun gatherArgumentsUntilMaximumOperationDepth(
        modelLimits: ModelLimits,
        graphQLSchema: GraphQLSchema,
        backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>
    ): (DomainSpecifiedDataElementSource.Builder) -> DomainSpecifiedDataElementSource.Builder {
        return { builder: DomainSpecifiedDataElementSource.Builder ->
            var b: DomainSpecifiedDataElementSource.Builder = builder
            while (
                backRefQueue.isNotEmpty() &&
                    backRefQueue.peekFirst().first.level() < modelLimits.maximumOperationDepth
            ) {
                val (p: GQLOperationPath, gfc: GraphQLFieldsContainer) = backRefQueue.pollFirst()
                b =
                    Traverser.breadthFirst(schemaElementTraversalFunction(graphQLSchema), p, b)
                        .traverse(
                            gfc,
                            SchemaElementTraverserVisitor(
                                DomainSpecifiedDataElementSourceVisitor(backRefQueue)
                            )
                        )
                        .successIfNonNull {
                            ServiceError.of(
                                "%s not returned for traversal",
                                TraverserResult::class.simpleName
                            )
                        }
                        .map(TraverserResult::getAccumulatedResult)
                        .filterInstanceOf<DomainSpecifiedDataElementSource.Builder>()
                        .orElseThrow()
            }
            b
        }
    }

    private class SchemaElementTraverserVisitor(
        private val graphQLTypeVisitor: GraphQLTypeVisitor
    ) : TraverserVisitor<GraphQLSchemaElement> {

        override fun enter(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return context.thisNode().accept(context, graphQLTypeVisitor)
        }

        override fun leave(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return TraversalControl.CONTINUE
        }

        override fun backRef(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return graphQLTypeVisitor.visitBackRef(context)
        }
    }

    private class DomainSpecifiedDataElementSourceVisitor(
        private val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>
    ) : GraphQLTypeVisitorStub() {

        companion object {
            private val logger: Logger = loggerFor<DomainSpecifiedDataElementSourceVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_field_definition: [ node.name: {} ]", node.name)
            val p: GQLOperationPath = createPathFromContext(node, context)
            val b: DomainSpecifiedDataElementSource.Builder =
                context.getCurrentAccumulate<DomainSpecifiedDataElementSource.Builder>()
            if (node.arguments.isNotEmpty()) {
                node.arguments
                    .asSequence()
                    .map { ga: GraphQLArgument -> p.transform { argument(ga.name) } to ga }
                    .forEach { (p: GQLOperationPath, ga: GraphQLArgument) ->
                        b.putArgumentForPathWithinDomain(p, ga)
                    }
            }
            context.setAccumulate(b)
            context.setVar(GQLOperationPath::class.java, p)
            return TraversalControl.CONTINUE
        }

        private fun createPathFromContext(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): GQLOperationPath {
            val p: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
            return when (
                val grandparent: GraphQLSchemaElement? = context.parentContext?.parentNode
            ) {
                is GraphQLInterfaceType -> {
                    when (val parent: GraphQLSchemaElement? = context.parentNode) {
                        is GraphQLObjectType -> {
                            if (grandparent.getFieldDefinition(node.name) != null) {
                                p.transform { appendField(node.name) }
                            } else {
                                p.transform { appendInlineFragment(parent.name, node.name) }
                            }
                        }
                        else -> {
                            throw ServiceError.of(
                                "unexpected traversal pattern: grandparent of %s is %s but parent is not %s",
                                GraphQLFieldDefinition::class.qualifiedName,
                                GraphQLInterfaceType::class.qualifiedName,
                                GraphQLObjectType::class.qualifiedName
                            )
                        }
                    }
                }
                else -> {
                    when (val parent: GraphQLSchemaElement? = context.parentNode) {
                        is GraphQLObjectType -> {
                            p.transform { appendField(node.name) }
                        }
                        null -> {
                            p.transform { appendField(node.name) }
                        }
                        else -> {
                            throw ServiceError.of(
                                "unexpected traversal pattern: parent of %s is not %s",
                                GraphQLFieldDefinition::class.qualifiedName,
                                GraphQLObjectType::class.qualifiedName
                            )
                        }
                    }
                }
            }
        }

        private fun extractParentPathContextVariableOrThrow(
            context: TraverserContext<GraphQLSchemaElement>
        ): GQLOperationPath {
            return Try.attemptNullable {
                    context.getVarFromParents<GQLOperationPath>(GQLOperationPath::class.java)
                }
                .flatMap(Try.Companion::fromOption)
                .orElseTry {
                    Try.attemptNullable { context.getSharedContextData<GQLOperationPath>() }
                        .flatMap(Try.Companion::fromOption)
                }
                .orElseThrow { _: Throwable ->
                    ServiceError.of("parent_path has not been set as variable in traverser_context")
                }
        }

        override fun visitBackRef(
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug(
                "visit_back_ref: [ context.parent_node[type,name]: { {}, {} }, context.this_node[type,name]: { {}, {} } ]",
                context.parentNode::class.simpleName,
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .getOrElse { "<NA>" },
                context.thisNode()::class.simpleName,
                context
                    .thisNode()
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .getOrElse { "<NA>" }
            )
            Option.catch { extractParentPathContextVariableOrThrow(context) }
                .zip(
                    context.parentNode.toOption().filterIsInstance<GraphQLFieldDefinition>(),
                    context.thisNode().toOption().filterIsInstance<GraphQLFieldsContainer>(),
                    ::Triple
                )
                .tap { t: Triple<GQLOperationPath, GraphQLFieldDefinition, GraphQLFieldsContainer>
                    ->
                    backRefQueue.offerLast(t.first to t.third)
                }
            return TraversalControl.CONTINUE
        }
    }
}
