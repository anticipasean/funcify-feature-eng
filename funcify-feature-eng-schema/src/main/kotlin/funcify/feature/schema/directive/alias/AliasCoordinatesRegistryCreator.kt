package funcify.feature.schema.directive.alias

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.directive.AliasDirective
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import org.slf4j.Logger

object AliasCoordinatesRegistryCreator : (GraphQLSchema) -> AliasCoordinatesRegistry {

    private const val METHOD_TAG: String = "alias_coordinates_registry_creator.invoke"
    private val logger: Logger = loggerFor<AliasCoordinatesRegistryCreator>()

    override fun invoke(graphQLSchema: GraphQLSchema): AliasCoordinatesRegistry {
        logger.debug("{}: []", METHOD_TAG)
        return graphQLSchema.queryType
            .toOption()
            .flatMap { got: GraphQLObjectType ->
                Traverser.breadthFirst(
                        schemaElementTraversalFunction(graphQLSchema),
                        null,
                        AliasCoordinatesRegistry.empty()
                    )
                    .traverse(
                        got,
                        AliasCoordinatesRegistryTraversalVisitor(
                            AliasCoordinatesRegistryElementVisitor()
                        )
                    )
                    .toOption()
                    .mapNotNull(TraverserResult::getAccumulatedResult)
                    .filterIsInstance<AliasCoordinatesRegistry>()
            }
            .getOrElse { AliasCoordinatesRegistry.empty() }
    }

    private fun schemaElementTraversalFunction(
        graphQLSchema: GraphQLSchema
    ): (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLInterfaceType -> {
                    sequenceOf(e.fieldDefinitions, graphQLSchema.getImplementations(e))
                        .flatten()
                        .toList()
                }
                is GraphQLFieldDefinition -> {
                    sequenceOf(e.appliedDirectives, e.arguments)
                        .flatten()
                        .plus(
                            e.type
                                .toOption()
                                .mapNotNull(GraphQLTypeUtil::unwrapAll)
                                .filterIsInstance<GraphQLFieldsContainer>()
                                .fold(::emptySequence, ::sequenceOf)
                        )
                        .toList()
                }
                is GraphQLArgument -> {
                    e.appliedDirectives
                }
                else -> {
                    emptyList()
                }
            }
        }
    }

    private class AliasCoordinatesRegistryTraversalVisitor(
        private val schemaElementVisitor: GraphQLTypeVisitor
    ) : TraverserVisitor<GraphQLSchemaElement> {

        override fun enter(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return context.thisNode().accept(context, schemaElementVisitor)
        }

        override fun leave(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return TraversalControl.CONTINUE
        }
    }

    private class AliasCoordinatesRegistryElementVisitor() : GraphQLTypeVisitorStub() {

        companion object {
            private const val ALIAS_ARGUMENT_NAME: String =
                AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME
            private val logger: Logger = loggerFor<AliasCoordinatesRegistryElementVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug(
                "visit_graphql_field_definition: [ parent_node [ name: {}, type: {} ], node.name: {} ]",
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .getOrElse { "<NA>" },
                context.parentNode.run { this::class }.simpleName.toOption().getOrElse { "<NA>" },
                node.name
            )
            if (
                context
                    .toOption()
                    .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentContext)
                    .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentNode)
                    .filterIsInstance<GraphQLInterfaceType>()
                    .map(GraphQLInterfaceType::getFieldDefinitions)
                    .fold(::emptySequence, List<GraphQLFieldDefinition>::asSequence)
                    .firstOrNone { fd: GraphQLFieldDefinition -> fd.name == node.name }
                    .mapNotNull { fd: GraphQLFieldDefinition ->
                        fd.getAppliedDirective(AliasDirective.name)
                    }
                    .isDefined()
            ) {
                updateAliasCoordinatesRegistryWithAliasForFieldOnAncestorInterface(node, context)
            }
            return TraversalControl.CONTINUE
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldOnAncestorInterface(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>,
        ) {
            when (val grandParentNode = context.parentContext?.parentNode) {
                is GraphQLInterfaceType -> {
                    when (val parentNode = context.parentNode) {
                        is GraphQLObjectType -> {
                            when (
                                val fieldDefOnGrandParentNode: GraphQLFieldDefinition? =
                                    grandParentNode.getField(node.name)
                            ) {
                                null -> {}
                                else -> {
                                    val appliedDirective: GraphQLAppliedDirective =
                                        fieldDefOnGrandParentNode.getAppliedDirective(
                                            AliasDirective.name
                                        )
                                    val name: String =
                                        appliedDirective
                                            .getArgument(
                                                AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME
                                            )
                                            ?.getValue<String?>()
                                            ?: ""
                                    if (name.isNotBlank()) {
                                        val fc: FieldCoordinates =
                                            FieldCoordinates.coordinates(parentNode, node)
                                        val acr: AliasCoordinatesRegistry =
                                            context.getCurrentAccumulate()
                                        context.setAccumulate(acr.registerFieldWithAlias(fc, name))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun visitGraphQLAppliedDirective(
            node: GraphQLAppliedDirective,
            context: TraverserContext<GraphQLSchemaElement>,
        ): TraversalControl {
            logger.debug(
                "visit_graphql_applied_directive: [ parent_node [ name: {}, type: {} ], node.name: {} ]",
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .getOrElse { "<NA>" },
                context.parentNode.run { this::class }.simpleName.toOption().getOrElse { "<NA>" },
                node.name
            )
            if (AliasDirective.name == node.name) {
                when (context.parentNode) {
                    is GraphQLFieldDefinition -> {
                        updateAliasCoordinatesRegistryWithAliasForField(node, context)
                    }
                    is GraphQLArgument -> {
                        updateAliasCoordinatesRegistryWithAliasForFieldArgument(node, context)
                    }
                }
            }
            return TraversalControl.CONTINUE
        }

        private fun updateAliasCoordinatesRegistryWithAliasForField(
            node: GraphQLAppliedDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ) {
            when (val grandParentNode = context.parentContext?.parentNode) {
                is GraphQLFieldsContainer -> {
                    val name: String =
                        node.getArgument(ALIAS_ARGUMENT_NAME)?.getValue<String?>() ?: ""
                    if (name.isNotBlank()) {
                        when (val parentNode = context.parentNode) {
                            is GraphQLFieldDefinition -> {
                                val fc: FieldCoordinates =
                                    FieldCoordinates.coordinates(grandParentNode, parentNode)
                                val acr: AliasCoordinatesRegistry = context.getCurrentAccumulate()
                                context.setAccumulate(acr.registerFieldWithAlias(fc, name))
                            }
                        }
                    }
                }
            }
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldArgument(
            node: GraphQLAppliedDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ) {
            when (val greatGrandParentNode = context.parentContext?.parentContext?.parentNode) {
                is GraphQLFieldsContainer -> {
                    val name: String =
                        node.getArgument(ALIAS_ARGUMENT_NAME)?.getValue<String?>() ?: ""
                    if (name.isNotBlank()) {
                        when (val grandParentNode = context.parentContext?.parentNode) {
                            is GraphQLFieldDefinition -> {
                                when (val parentNode = context.parentNode) {
                                    is GraphQLArgument -> {
                                        val fc: FieldCoordinates =
                                            FieldCoordinates.coordinates(
                                                greatGrandParentNode,
                                                grandParentNode
                                            )
                                        val argName: String = parentNode.name
                                        val acr: AliasCoordinatesRegistry =
                                            context.getCurrentAccumulate()
                                        context.setAccumulate(
                                            acr.registerFieldArgumentWithAlias(fc to argName, name)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun visitGraphQLArgument(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug(
                "visit_graphql_argument: [ parent_node [ name: {}, type: {} ], node.name: {} ]",
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .getOrElse { "<NA>" },
                context.parentNode.run { this::class }.simpleName.toOption().getOrElse { "<NA>" },
                node.name
            )
            if (
                context
                    .toOption()
                    .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentContext)
                    .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentContext)
                    .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentNode)
                    .filterIsInstance<GraphQLInterfaceType>()
                    .zip(
                        context
                            .toOption()
                            .mapNotNull(TraverserContext<GraphQLSchemaElement>::getParentNode)
                            .filterIsInstance<GraphQLFieldDefinition>()
                    )
                    .flatMap { (git: GraphQLInterfaceType, gfd: GraphQLFieldDefinition) ->
                        git.toOption()
                            .map(GraphQLInterfaceType::getFieldDefinitions)
                            .fold(::emptySequence, List<GraphQLFieldDefinition>::asSequence)
                            .firstOrNone { fd: GraphQLFieldDefinition -> fd.name == gfd.name }
                            .mapNotNull { fd: GraphQLFieldDefinition -> fd.getArgument(node.name) }
                            .mapNotNull { ga: GraphQLArgument ->
                                ga.getAppliedDirectives(AliasDirective.name)
                            }
                            .filter(List<GraphQLAppliedDirective>::isNotEmpty)
                    }
                    .isDefined()
            ) {
                updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnAncestorInterface(
                    node,
                    context
                )
            }
            return TraversalControl.CONTINUE
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnAncestorInterface(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ) {
            when (val greatGrandParentNode = context.parentContext?.parentContext?.parentNode) {
                is GraphQLInterfaceType -> {
                    when (val grandParentNode = context.parentContext?.parentNode) {
                        is GraphQLObjectType -> {
                            when (val parentNode = context.parentNode) {
                                is GraphQLFieldDefinition -> {
                                    val correspondingInterfaceFieldDef: GraphQLFieldDefinition? =
                                        greatGrandParentNode.getFieldDefinition(parentNode.name)
                                    val correspondingInterfaceFieldArg: GraphQLArgument? =
                                        correspondingInterfaceFieldDef?.getArgument(node.name)
                                    val correspondingInterfaceFieldArgAliasDirectives:
                                        List<GraphQLAppliedDirective> =
                                        correspondingInterfaceFieldArg?.getAppliedDirectives(
                                            AliasDirective.name
                                        )
                                            ?: emptyList()
                                    if (
                                        correspondingInterfaceFieldDef != null &&
                                            correspondingInterfaceFieldArg != null &&
                                            correspondingInterfaceFieldArgAliasDirectives
                                                .isNotEmpty()
                                    ) {
                                        val fc: FieldCoordinates =
                                            FieldCoordinates.coordinates(
                                                grandParentNode,
                                                parentNode
                                            )
                                        val argName: String = parentNode.name
                                        val acr: AliasCoordinatesRegistry =
                                            context.getCurrentAccumulate()
                                        context.setAccumulate(
                                            correspondingInterfaceFieldArgAliasDirectives
                                                .asSequence()
                                                .filter { gad: GraphQLAppliedDirective ->
                                                    (gad.getArgument(ALIAS_ARGUMENT_NAME)
                                                            ?.getValue<String?>()
                                                            ?: "")
                                                        .isNotBlank()
                                                }
                                                .mapNotNull { gad: GraphQLAppliedDirective ->
                                                    gad.getArgument(ALIAS_ARGUMENT_NAME)
                                                        .getValue<String>()
                                                }
                                                .fold(acr) {
                                                    a: AliasCoordinatesRegistry,
                                                    alias: String ->
                                                    a.registerFieldArgumentWithAlias(
                                                        fc to argName,
                                                        alias
                                                    )
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
