package funcify.feature.materializer.model

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.recurseBreadthFirst
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.FieldDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeDefinition
import graphql.language.TypeName
import graphql.schema.*
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeUtil
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
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
                traverseTransformerElementTypeCreatingTransformerSpecifiedTransformerSources(
                        featureEngineeringModel,
                        graphQLSchema,
                        p,
                        gfc
                    )
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
            .asIterable()
    }

    private fun traverseTransformerElementTypeCreatingTransformerSpecifiedTransformerSources(
        fem: FeatureEngineeringModel,
        gs: GraphQLSchema,
        transformerElementPath: GQLOperationPath,
        transformerElementTypeFieldsContainer: GraphQLFieldsContainer,
    ): Try<Sequence<TransformerSpecifiedTransformerSource>> {
        val tsByFieldDefName: PersistentMap<String, TransformerSource> =
            extractTransformerSourcesByNameFromFeatureEngineeringModel(fem)
        return Try.attemptNullable({
                Traverser.breadthFirst(
                        schemaElementTraversalFunction(gs),
                        transformerElementPath,
                        TransformerSpecifiedTransformerSourceContext(
                            transformerSourcesByFieldName = tsByFieldDefName,
                            transformerSpecifiedTransformerSources = persistentListOf()
                        )
                    )
                    .traverse(
                        transformerElementTypeFieldsContainer,
                        SchemaElementTraverserVisitor(
                            graphQLTypeVisitor = TransformerSpecifiedTransformerSourceVisitor()
                        )
                    )
            }) {
                ServiceError.of("context instance failed to be passed back from visitor")
            }
            .map(TraverserResult::getAccumulatedResult)
            .filterInstanceOf<TransformerSpecifiedTransformerSourceContext>()
            .map { c: TransformerSpecifiedTransformerSourceContext ->
                c.transformerSpecifiedTransformerSources.asSequence()
            }
    }

    private fun extractTransformerSourcesByNameFromFeatureEngineeringModel(
        fem: FeatureEngineeringModel
    ): PersistentMap<String, TransformerSource> {
        return fem.transformerSourcesByName.values
            .asSequence()
            .flatMap { ts: TransformerSource ->
                val tdr: TypeDefinitionRegistry =
                    TypeDefinitionRegistry().apply { addAll(ts.sourceSDLDefinitions) }
                tdr.getType(QUERY_OBJECT_TYPE_NAME)
                    .toOption()
                    .filterIsInstance<ObjectTypeDefinition>()
                    .sequence()
                    .recurseBreadthFirst { itd: ImplementingTypeDefinition<*> ->
                        shiftLeftImplementingTypeDefinitionsSelectRightTransformerNames(itd, tdr)
                    }
                    .map { name: String -> name to ts }
            }
            .reducePairsToPersistentMap()
    }

    private fun shiftLeftImplementingTypeDefinitionsSelectRightTransformerNames(
        itd: ImplementingTypeDefinition<*>,
        tdr: TypeDefinitionRegistry,
    ): Sequence<Either<ImplementingTypeDefinition<*>, String>> {
        return when (itd) {
            is InterfaceTypeDefinition -> {
                tdr.getImplementationsOf(itd).asSequence().map(ObjectTypeDefinition::left)
            }
            is ObjectTypeDefinition -> {
                itd.fieldDefinitions.asSequence().flatMap { fd: FieldDefinition ->
                    when {
                        fd.inputValueDefinitions.isNotEmpty() -> {
                            sequenceOf(fd.name.right())
                        }
                        TypeUtil.unwrapAll(fd.type)
                            .toOption()
                            .flatMap { tn: TypeName -> tdr.getType(tn).toOption() }
                            .filterNot { td: TypeDefinition<*> ->
                                td is ImplementingTypeDefinition<*>
                            }
                            .isDefined() -> {
                            sequenceOf(fd.name.right())
                        }
                        else -> {
                            TypeUtil.unwrapAll(fd.type)
                                .toOption()
                                .flatMap { tn: TypeName -> tdr.getType(tn).toOption() }
                                .filterIsInstance<ImplementingTypeDefinition<*>>()
                                .map(ImplementingTypeDefinition<*>::left)
                                .sequence()
                        }
                    }
                }
            }
            else -> {
                emptySequence()
            }
        }
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

    private data class TransformerSpecifiedTransformerSourceContext(
        val transformerSourcesByFieldName: PersistentMap<String, TransformerSource>,
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
            val p: GQLOperationPath = createPathFromContext(node, context)
            if (
                !node.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapAll)
                    .filterIsInstance<GraphQLImplementingType>()
                    .isDefined()
            ) {
                updateContextWithTransformerGraphQLFieldDefinition(context, p, node)
            }
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
                    .successIfDefined {
                        ServiceError.of(
                            """parent of %s [ name: %s ] is not %s""".flatten(),
                            GraphQLFieldDefinition::class.qualifiedName,
                            fieldDefinition.name,
                            GraphQLImplementingType::class.qualifiedName
                        )
                    }
                    .orElseThrow()
            val ts: TransformerSource =
                c.transformerSourcesByFieldName
                    .getOrNone(fieldDefinition.name)
                    .successIfDefined {
                        ServiceError.of(
                            "transformer_source not found for [ field_definition.name: %s ]",
                            fieldDefinition.name
                        )
                    }
                    .orElseThrow()
            val tsts: TransformerSpecifiedTransformerSource =
                DefaultTransformerSpecifiedTransformerSource.builder()
                    .transformerFieldCoordinates(
                        FieldCoordinates.coordinates(parentTypeName, fieldDefinition.name)
                    )
                    .transformerSource(ts)
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
    }
}
