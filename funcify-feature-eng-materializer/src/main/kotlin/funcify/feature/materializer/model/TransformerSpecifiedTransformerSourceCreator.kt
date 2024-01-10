package funcify.feature.materializer.model

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
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
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger

internal object TransformerSpecifiedTransformerSourceCreator :
    (FeatureEngineeringModel, GraphQLSchema) -> Iterable<TransformerSpecifiedTransformerSource> {

    private const val TYPE_NAME: String = "transformer_specified_transformer_source_creator"
    private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    private val logger: Logger = loggerFor<TransformerSpecifiedTransformerSourceCreator>()

    override fun invoke(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): Iterable<TransformerSpecifiedTransformerSource> {
        logger.info("{}.invoke: [ ]", TYPE_NAME)
        return graphQLSchema.queryType
            .toOption()
            .flatMap { got: GraphQLObjectType ->
                when {
                    got.name == featureEngineeringModel.transformerFieldCoordinates.typeName -> {
                        got.getFieldDefinition(
                                featureEngineeringModel.transformerFieldCoordinates.fieldName
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
                val tsByFieldDefName: ImmutableMap<String, TransformerSource> =
                    extractTransformerSourceByFieldDefinitionNameMapFromFeatureEngineeringModel(
                        featureEngineeringModel
                    )
                gfc.fieldDefinitions
                    .asSequence()
                    .map { fd: GraphQLFieldDefinition ->
                        tsByFieldDefName.getOrNone(fd.name).map { ts: TransformerSource ->
                            fd to ts
                        }
                    }
                    .flatMapOptions()
                    .flatMap { (fd: GraphQLFieldDefinition, ts: TransformerSource) ->
                        Traverser.breadthFirst(
                                schemaElementTraversalFunction(graphQLSchema),
                                p,
                                TransformerSpecifiedTransformerSourceContext(
                                    transformerTypeName = gfc.name,
                                    transformerSource = ts,
                                    transformerSpecifiedTransformerSources = persistentListOf()
                                )
                            )
                            .traverse(
                                fd,
                                SchemaElementTraverserVisitor(
                                    graphQLTypeVisitor =
                                        TransformerSpecifiedTransformerSourceVisitor()
                                )
                            )
                            .toOption()
                            .mapNotNull(TraverserResult::getAccumulatedResult)
                            .filterIsInstance<TransformerSpecifiedTransformerSourceContext>()
                            .map { c: TransformerSpecifiedTransformerSourceContext ->
                                c.transformerSpecifiedTransformerSources.asSequence()
                            }
                            .successIfDefined {
                                ServiceError.of(
                                    "context instance failed to be passed back from visitor"
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
                            .fold(::identity) { _: Throwable -> emptySequence() }
                    }
            }
            .asIterable()
    }

    private fun extractTransformerSourceByFieldDefinitionNameMapFromFeatureEngineeringModel(
        featureEngineeringModel: FeatureEngineeringModel
    ): ImmutableMap<String, TransformerSource> {
        return featureEngineeringModel.transformerSourcesByName.values
            .asSequence()
            .flatMap { ts: TransformerSource ->
                ts.sourceSDLDefinitions
                    .asSequence()
                    .firstOrNone { sd: SDLDefinition<*> ->
                        sd is ObjectTypeDefinition && QUERY_OBJECT_TYPE_NAME == sd.name
                    }
                    .filterIsInstance<ObjectTypeDefinition>()
                    .map(ObjectTypeDefinition::getFieldDefinitions)
                    .fold(::emptyList, ::identity)
                    .asSequence()
                    .map { fd: FieldDefinition -> fd.name to ts }
            }
            .reducePairsToPersistentMap()
    }

    private fun schemaElementTraversalFunction(
        graphQLSchema: GraphQLSchema
    ): (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLInterfaceType -> {
                    e.fieldDefinitions
                        .asSequence()
                        .plus(graphQLSchema.getImplementations(e))
                        .toList()
                }
                is GraphQLObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLTypeReference -> {
                    graphQLSchema
                        .getType(e.name)
                        .toOption()
                        .flatMap { gt: GraphQLType ->
                            when (gt) {
                                is GraphQLFieldsContainer -> gt.some()
                                is GraphQLInputFieldsContainer -> gt.some()
                                else -> none()
                            }
                        }
                        .fold(::emptyList, ::listOf)
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
                    emptyList<GraphQLSchemaElement>()
                }
            }
        }
    }

    private data class TransformerSpecifiedTransformerSourceContext(
        val transformerTypeName: String,
        val transformerSource: TransformerSource,
        val transformerSpecifiedTransformerSources:
            PersistentList<TransformerSpecifiedTransformerSource>
    )

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

    private class TransformerSpecifiedTransformerSourceVisitor : GraphQLTypeVisitorStub() {
        companion object {
            private val logger: Logger = loggerFor<TransformerSpecifiedTransformerSourceVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_field_definition: [ node.name: {} ]", node.name)
            when (
                val parentNode: GraphQLFieldsContainer? =
                    context.parentNode as? GraphQLFieldsContainer
            ) {
                is GraphQLInterfaceType -> {
                    val p: GQLOperationPath =
                        extractParentPathContextVariableOrThrow(context).transform {
                            field(node.name)
                        }
                    if (node.arguments.isNotEmpty()) {
                        updateContextWithTransformerGraphQLFieldDefinition(context, p, node)
                    }
                    context.setVar(GQLOperationPath::class.java, p)
                }
                is GraphQLObjectType -> {
                    when (
                        val grandparentNode: GraphQLFieldsContainer? =
                            context.parentContext?.parentNode as? GraphQLFieldsContainer
                    ) {
                        is GraphQLInterfaceType -> {
                            if (grandparentNode.getFieldDefinition(node.name) == null) {
                                val p: GQLOperationPath =
                                    extractParentPathContextVariableOrThrow(context).transform {
                                        inlineFragment(parentNode.name, node.name)
                                    }
                                if (node.arguments.isNotEmpty()) {
                                    updateContextWithTransformerGraphQLFieldDefinition(
                                        context,
                                        p,
                                        node
                                    )
                                }
                                context.setVar(GQLOperationPath::class.java, p)
                            }
                        }
                        else -> {
                            val p: GQLOperationPath =
                                extractParentPathContextVariableOrThrow(context).transform {
                                    field(node.name)
                                }
                            if (node.arguments.isNotEmpty()) {
                                updateContextWithTransformerGraphQLFieldDefinition(context, p, node)
                            }
                            context.setVar(GQLOperationPath::class.java, p)
                        }
                    }
                }
                else -> {
                    val p: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
                    if (node.arguments.isNotEmpty()) {
                        updateContextWithTransformerGraphQLFieldDefinition(context, p, node)
                    }
                    context.setVar(GQLOperationPath::class.java, p)
                }
            }
            return TraversalControl.CONTINUE
        }

        private fun updateContextWithTransformerGraphQLFieldDefinition(
            context: TraverserContext<GraphQLSchemaElement>,
            path: GQLOperationPath,
            fieldDefinition: GraphQLFieldDefinition,
        ) {
            val c: TransformerSpecifiedTransformerSourceContext = context.getCurrentAccumulate()
            val parentTypeName: String =
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLImplementingType>()
                    .map(GraphQLImplementingType::getName)
                    .getOrElse { c.transformerTypeName }
            val tsts: TransformerSpecifiedTransformerSource =
                DefaultTransformerSpecifiedTransformerSource.builder()
                    .transformerFieldCoordinates(
                        FieldCoordinates.coordinates(parentTypeName, fieldDefinition.name)
                    )
                    .transformerSource(c.transformerSource)
                    .transformerPath(path)
                    .transformerFieldDefinition(fieldDefinition)
                    .build()
            context.setAccumulate(
                c.copy(
                    transformerSpecifiedTransformerSources =
                        c.transformerSpecifiedTransformerSources.add(tsts)
                )
            )
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
    }
}
