package funcify.feature.materializer.model

import arrow.core.None
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.directive.TransformDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PairExtensions.fold
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.SDLDefinition
import graphql.language.Value
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

internal object FeatureSpecifiedFeatureCalculatorCreator :
    (FeatureEngineeringModel, GraphQLSchema) -> Iterable<FeatureSpecifiedFeatureCalculator> {

    private const val TYPE_NAME: String = "feature_specified_feature_calculator_creator"
    private const val METHOD_TAG: String = TYPE_NAME + ".invoke"
    private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    private val logger: Logger = loggerFor<FeatureSpecifiedFeatureCalculatorCreator>()

    override fun invoke(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): Iterable<FeatureSpecifiedFeatureCalculator> {
        logger.info("{}: [ ]", METHOD_TAG)
        return graphQLSchema.queryType
            .toOption()
            .flatMap { got: GraphQLObjectType ->
                when {
                    got.name == featureEngineeringModel.featureFieldCoordinates.typeName -> {
                        got.getFieldDefinition(
                                featureEngineeringModel.featureFieldCoordinates.fieldName
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
                val fcByFieldDefName: ImmutableMap<String, FeatureCalculator> =
                    extractFeatureCalculatorByFieldDefinitionNameMapFromFeatureEngineeringModel(
                        featureEngineeringModel
                    )
                gfc.fieldDefinitions
                    .asSequence()
                    .map { fd: GraphQLFieldDefinition ->
                        fcByFieldDefName.getOrNone(fd.name).map { fc: FeatureCalculator ->
                            fd to fc
                        }
                    }
                    .flatMapOptions()
                    .flatMap { (fd: GraphQLFieldDefinition, fc: FeatureCalculator) ->
                        Traverser.breadthFirst(
                                schemaElementTraversalFunction(graphQLSchema),
                                p,
                                FeatureSpecifiedFeatureCalculatorContext(
                                    featureTypeName = gfc.name,
                                    featureCalculator = fc,
                                    featureSpecifiedFeatureCalculators = persistentListOf(),
                                    errors = persistentListOf()
                                )
                            )
                            .traverse(
                                fd,
                                SchemaElementTraverserVisitor(
                                    graphQLTypeVisitor = FeatureSpecifiedFeatureCalculatorVisitor()
                                )
                            )
                            .toOption()
                            .mapNotNull(TraverserResult::getAccumulatedResult)
                            .filterIsInstance<FeatureSpecifiedFeatureCalculatorContext>()
                            .successIfDefined {
                                ServiceError.of(
                                    "context instance failed to be passed back from visitor"
                                )
                            }
                            .flatMap { c: FeatureSpecifiedFeatureCalculatorContext ->
                                when {
                                    c.errors.isNotEmpty() -> {
                                        Try.failure(
                                            c.errors.fold(
                                                ServiceError.of(
                                                    "error(s) occurred when creating %s",
                                                    FeatureSpecifiedFeatureCalculator::class
                                                        .simpleName
                                                ),
                                                ServiceError::plus
                                            )
                                        )
                                    }
                                    else -> {
                                        Try.success(
                                            c.featureSpecifiedFeatureCalculators.asSequence()
                                        )
                                    }
                                }
                            }
                            .peekIfFailure { t: Throwable ->
                                logger.warn(
                                    "{}: [ status: error occurred ][ type: {}, json/message: {} ]",
                                    METHOD_TAG,
                                    t::class.simpleName,
                                    t.toOption()
                                        .filterIsInstance<ServiceError>()
                                        .map(ServiceError::toJsonNode)
                                        .map(JsonNode::toString)
                                        .getOrElse { t.message }
                                )
                            }
                            .orElseThrow()
                    }
            }
            .asIterable()
    }

    private fun extractFeatureCalculatorByFieldDefinitionNameMapFromFeatureEngineeringModel(
        featureEngineeringModel: FeatureEngineeringModel
    ): ImmutableMap<String, FeatureCalculator> {
        return featureEngineeringModel.featureCalculatorsByName.values
            .asSequence()
            .flatMap { fc: FeatureCalculator ->
                fc.sourceSDLDefinitions
                    .asSequence()
                    .firstOrNone { sd: SDLDefinition<*> ->
                        sd is ObjectTypeDefinition && QUERY_OBJECT_TYPE_NAME == sd.name
                    }
                    .filterIsInstance<ObjectTypeDefinition>()
                    .map(ObjectTypeDefinition::getFieldDefinitions)
                    .fold(::emptyList, ::identity)
                    .asSequence()
                    .map { fd: FieldDefinition -> fd.name to fc }
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

    private data class FeatureSpecifiedFeatureCalculatorContext(
        val featureTypeName: String,
        val featureCalculator: FeatureCalculator,
        val featureSpecifiedFeatureCalculators: PersistentList<FeatureSpecifiedFeatureCalculator>,
        val errors: PersistentList<ServiceError>
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

    private class FeatureSpecifiedFeatureCalculatorVisitor : GraphQLTypeVisitorStub() {
        companion object {
            private val logger: Logger = loggerFor<FeatureSpecifiedFeatureCalculatorVisitor>()
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
                        updateContextWithFeatureGraphQLFieldDefinition(context, p, node)
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
                                    updateContextWithFeatureGraphQLFieldDefinition(context, p, node)
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
                                updateContextWithFeatureGraphQLFieldDefinition(context, p, node)
                            }
                            context.setVar(GQLOperationPath::class.java, p)
                        }
                    }
                }
                else -> {
                    val p: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
                    if (node.arguments.isNotEmpty()) {
                        updateContextWithFeatureGraphQLFieldDefinition(context, p, node)
                    }
                    context.setVar(GQLOperationPath::class.java, p)
                }
            }
            return TraversalControl.CONTINUE
        }

        private fun updateContextWithFeatureGraphQLFieldDefinition(
            context: TraverserContext<GraphQLSchemaElement>,
            path: GQLOperationPath,
            fieldDefinition: GraphQLFieldDefinition,
        ) {
            val c: FeatureSpecifiedFeatureCalculatorContext = context.getCurrentAccumulate()
            val parentTypeName: String =
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLImplementingType>()
                    .map(GraphQLImplementingType::getName)
                    .getOrElse { c.featureTypeName }
            val tfc: Try<FieldCoordinates> =
                extractTransformerFieldCoordinatesForFeatureFieldDefinition(fieldDefinition)
            if (tfc.isFailure()) {
                context.setAccumulate(
                    c.copy(
                        errors =
                            c.errors.add(
                                tfc.getFailure().orNull() as? ServiceError
                                    ?: throw ServiceError.of(
                                        "error for transformer_field_coordinates extraction not of type %s",
                                        ServiceError::class.simpleName
                                    )
                            )
                    )
                )
                return
            }
            val fsfc: FeatureSpecifiedFeatureCalculator =
                DefaultFeatureSpecifiedFeatureCalculator.builder()
                    .featureFieldCoordinates(
                        FieldCoordinates.coordinates(parentTypeName, fieldDefinition.name)
                    )
                    .featureCalculator(c.featureCalculator)
                    .featurePath(path)
                    .featureFieldDefinition(fieldDefinition)
                    .transformerFieldCoordinates(tfc.orElseThrow())
                    .build()
            context.setAccumulate(
                c.copy(
                    featureSpecifiedFeatureCalculators =
                        c.featureSpecifiedFeatureCalculators.add(fsfc)
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

        private fun extractTransformerFieldCoordinatesForFeatureFieldDefinition(
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Try<FieldCoordinates> {
            return graphQLFieldDefinition
                .getAppliedDirective(TransformDirective.name)
                .toOption()
                .mapNotNull { gad: GraphQLAppliedDirective ->
                    gad.getArgument(TransformDirective.COORDINATES_INPUT_VALUE_DEFINITION_NAME)
                }
                .flatMap { ada: GraphQLAppliedDirectiveArgument ->
                    when {
                        ada.argumentValue.isLiteral -> {
                            ada.argumentValue
                                .toOption()
                                .mapNotNull(InputValueWithState::getValue)
                                .filterIsInstance<Value<*>>()
                                .flatMap(GraphQLValueToJsonNodeConverter)
                                .flatMap { jn: JsonNode ->
                                    jn.get(TransformDirective.TYPE_NAME_INPUT_VALUE_DEFINITION_NAME)
                                        .toOption()
                                        .zip(
                                            jn.get(
                                                    TransformDirective
                                                        .FIELD_NAME_INPUT_VALUE_DEFINITION_NAME
                                                )
                                                .toOption()
                                        )
                                        .map { p: Pair<JsonNode, JsonNode> ->
                                            p.first.asText("") to p.second.asText("")
                                        }
                                        .filter { p: Pair<String, String> ->
                                            p.first.isNotBlank() && p.second.isNotBlank()
                                        }
                                        .map { p: Pair<String, String> ->
                                            p.fold(FieldCoordinates::coordinates)
                                        }
                                }
                        }
                        else -> {
                            None
                        }
                    }
                }
                .successIfDefined {
                    ServiceError.of(
                        "unable to determine transformer_field_coordinates for feature graphql_field_definition [ name: %s ]",
                        graphQLFieldDefinition.name
                    )
                }
        }
    }
}
