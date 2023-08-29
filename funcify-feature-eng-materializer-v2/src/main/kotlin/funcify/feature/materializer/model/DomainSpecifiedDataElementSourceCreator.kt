package funcify.feature.materializer.model

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
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.SDLDefinition
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger

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
                        Traverser.breadthFirst(
                                fieldDefinitionTraversalFunction(),
                                p,
                                DefaultDomainSpecifiedDataElementSource.builder()
                                    .domainFieldCoordinates(
                                        FieldCoordinates.coordinates(gfc.name, fd.name)
                                    )
                                    .dataElementSource(des)
                            )
                            .traverse(
                                fd,
                                SchemaElementTraverserVisitor(
                                    graphQLTypeVisitor = DomainSpecifiedDataElementSourceVisitor()
                                )
                            )
                            .toOption()
                            .mapNotNull(TraverserResult::getAccumulatedResult)
                            .filterIsInstance<DomainSpecifiedDataElementSource.Builder>()
                            .map { b: DomainSpecifiedDataElementSource.Builder ->
                                Try.attempt { b.build() }
                            }
                            .getOrElse {
                                Try.failure(
                                    ServiceError.of(
                                        "builder instance failed to be passed back from visitor"
                                    )
                                )
                            }
                            .peekIfFailure { t: Throwable ->
                                logger.warn(
                                    "{}.invoke: [ status: error occurred ][ type: {}, message: {} ]",
                                    TYPE_NAME,
                                    t::class.simpleName,
                                    t.message
                                )
                            }
                            .getSuccess()
                    }
            }
            .flatMapOptions()
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

    private fun fieldDefinitionTraversalFunction():
        (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLFieldDefinition -> {
                    e.arguments
                }
                else -> {
                    emptyList<GraphQLSchemaElement>()
                }
            }
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

    private class DomainSpecifiedDataElementSourceVisitor : GraphQLTypeVisitorStub() {
        companion object {
            private val logger: Logger = loggerFor<DomainSpecifiedDataElementSourceVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_field_definition: [ node.name: {} ]", node.name)
            val p: GQLOperationPath =
                extractParentPathContextVariableOrThrow(context).transform { field(node.name) }
            val b: DomainSpecifiedDataElementSource.Builder =
                context.getCurrentAccumulate<DomainSpecifiedDataElementSource.Builder>()
            context.setAccumulate(b.domainPath(p).domainFieldDefinition(node))
            context.setVar(GQLOperationPath::class.java, p)
            return TraversalControl.CONTINUE
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

        override fun visitGraphQLArgument(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_argument: [ node.name: {} ]", node.name)
            val p: GQLOperationPath =
                extractParentPathContextVariableOrThrow(context).transform { argument(node.name) }
            val b: DomainSpecifiedDataElementSource.Builder =
                context.getCurrentAccumulate<DomainSpecifiedDataElementSource.Builder>()
            if (node.hasSetDefaultValue()) {
                context.setAccumulate(
                    b.putArgumentForPath(p, node)
                        .putArgumentForName(node.name, node)
                        .putArgumentsWithDefaultValuesForName(node.name, node)
                )
            } else {
                context.setAccumulate(
                    b.putArgumentForPath(p, node).putArgumentForName(node.name, node)
                )
            }
            return TraversalControl.CONTINUE
        }
    }
}
