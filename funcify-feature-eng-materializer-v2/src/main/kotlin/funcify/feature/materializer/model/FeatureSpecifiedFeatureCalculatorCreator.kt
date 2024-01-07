package funcify.feature.materializer.model

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.right
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
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PairExtensions.fold
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
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.GraphQLUnmodifiedType
import graphql.schema.InputValueWithState
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
        logger.info(
            "{}: [ feature_calculators_by_name.size: {} ]",
            METHOD_TAG,
            featureEngineeringModel.featureCalculatorsByName.size
        )
        return graphQLSchema.queryType
            .toOption()
            .flatMap { got: GraphQLObjectType ->
                extractFeatureElementTypePathAndDefinitionFromQueryObjectType(
                    featureEngineeringModel,
                    got
                )
            }
            .flatMap { (p: GQLOperationPath, fd: GraphQLFieldDefinition) ->
                GraphQLTypeUtil.unwrapAll(fd.type)
                    .toOption()
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .map { gfc: GraphQLFieldsContainer -> p to gfc }
            }
            .fold(::emptySequence) { (p: GQLOperationPath, gfc: GraphQLFieldsContainer) ->
                traverseFeatureElementTypeCreatingFeatureSpecifiedFeatureCalculators(
                        featureEngineeringModel,
                        graphQLSchema,
                        p,
                        gfc
                    )
                    .peek({ _: Sequence<FeatureSpecifiedFeatureCalculator> ->
                        logger.debug("{}: [ status: successful ]", METHOD_TAG)
                    }) { t: Throwable ->
                        logger.warn(
                            "{}: [ status: failed ][ type: {}, json/message: {} ]",
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
            .asIterable()
    }

    private fun extractFeatureElementTypePathAndDefinitionFromQueryObjectType(
        fem: FeatureEngineeringModel,
        queryObjectType: GraphQLObjectType,
    ): Option<Pair<GQLOperationPath, GraphQLFieldDefinition>> {
        return when {
            queryObjectType.name == fem.featureFieldCoordinates.typeName -> {
                queryObjectType
                    .getFieldDefinition(fem.featureFieldCoordinates.fieldName)
                    .toOption()
                    .map { gfd: GraphQLFieldDefinition ->
                        GQLOperationPath.getRootPath().transform { appendField(gfd.name) } to gfd
                    }
            }
            else -> {
                none()
            }
        }
    }

    private fun traverseFeatureElementTypeCreatingFeatureSpecifiedFeatureCalculators(
        fem: FeatureEngineeringModel,
        gs: GraphQLSchema,
        featureElementPath: GQLOperationPath,
        featureElementTypeFieldsContainer: GraphQLFieldsContainer,
    ): Try<Sequence<FeatureSpecifiedFeatureCalculator>> {
        val fcByFieldDefName: PersistentMap<String, FeatureCalculator> =
            extractFeatureCalculatorByFieldDefinitionNameMapFromFeatureEngineeringModel(fem)
        return Try.attemptNullable({
                Traverser.breadthFirst(
                        schemaElementTraversalFunction(gs),
                        featureElementPath,
                        FeatureSpecifiedFeatureCalculatorContext(
                            featureCalculatorsByName = fcByFieldDefName,
                            featureSpecifiedFeatureCalculators = persistentListOf()
                        )
                    )
                    .traverse(
                        featureElementTypeFieldsContainer,
                        SchemaElementTraverserVisitor(
                            graphQLTypeVisitor = FeatureSpecifiedFeatureCalculatorVisitor()
                        )
                    )
            }) {
                ServiceError.of("context instance failed to be passed back from visitor")
            }
            .map(TraverserResult::getAccumulatedResult)
            .filterInstanceOf<FeatureSpecifiedFeatureCalculatorContext>()
            .map { c: FeatureSpecifiedFeatureCalculatorContext ->
                c.featureSpecifiedFeatureCalculators.asSequence()
            }
    }

    private fun extractFeatureCalculatorByFieldDefinitionNameMapFromFeatureEngineeringModel(
        fem: FeatureEngineeringModel
    ): PersistentMap<String, FeatureCalculator> {
        return fem.featureCalculatorsByName.values
            .asSequence()
            .flatMap { fc: FeatureCalculator ->
                val tdr: TypeDefinitionRegistry =
                    TypeDefinitionRegistry().apply { addAll(fc.sourceSDLDefinitions) }
                tdr.getType(QUERY_OBJECT_TYPE_NAME)
                    .toOption()
                    .filterIsInstance<ObjectTypeDefinition>()
                    .sequence()
                    .recurseBreadthFirst { itd: ImplementingTypeDefinition<*> ->
                        shiftLeftImplementingTypeDefinitionsSelectRightFeatureNames(itd, tdr)
                    }
                    .map { name: String -> name to fc }
            }
            .reducePairsToPersistentMap()
    }

    private fun shiftLeftImplementingTypeDefinitionsSelectRightFeatureNames(
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

    private data class FeatureSpecifiedFeatureCalculatorContext(
        val featureCalculatorsByName: PersistentMap<String, FeatureCalculator>,
        val featureSpecifiedFeatureCalculators: PersistentList<FeatureSpecifiedFeatureCalculator>,
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
            val p: GQLOperationPath = createPathFromContext(node, context)
            if (
                !node.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapAll)
                    .filterIsInstance<GraphQLImplementingType>()
                    .isDefined()
            ) {
                updateContextWithFeatureGraphQLFieldDefinition(context, p, node)
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

        private fun updateContextWithFeatureGraphQLFieldDefinition(
            context: TraverserContext<GraphQLSchemaElement>,
            path: GQLOperationPath,
            fieldDefinition: GraphQLFieldDefinition,
        ) {
            val c: FeatureSpecifiedFeatureCalculatorContext = context.getCurrentAccumulate()
            val fc: FeatureCalculator =
                c.featureCalculatorsByName
                    .getOrNone(fieldDefinition.name)
                    .successIfDefined {
                        ServiceError.of(
                            "feature_calculator not found for feature %s [ name: %s ]",
                            GraphQLFieldDefinition::class.qualifiedName,
                            fieldDefinition.name
                        )
                    }
                    .orElseThrow()
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
            val tfc: FieldCoordinates =
                extractTransformerFieldCoordinatesForFeatureFieldDefinition(fieldDefinition)
                    .orElseThrow()
            val fsfc: FeatureSpecifiedFeatureCalculator =
                DefaultFeatureSpecifiedFeatureCalculator.builder()
                    .featureFieldCoordinates(
                        FieldCoordinates.coordinates(parentTypeName, fieldDefinition.name)
                    )
                    .featureCalculator(fc)
                    .featurePath(path)
                    .featureFieldDefinition(fieldDefinition)
                    .transformerFieldCoordinates(tfc)
                    .build()
            context.setAccumulate(
                c.copy(
                    featureSpecifiedFeatureCalculators =
                        c.featureSpecifiedFeatureCalculators.add(fsfc)
                )
            )
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
