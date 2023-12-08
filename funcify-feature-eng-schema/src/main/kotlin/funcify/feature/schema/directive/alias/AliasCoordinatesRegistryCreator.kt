package funcify.feature.schema.directive.alias

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.directive.AliasDirective
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
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
                    graphQLSchema.getImplementations(e)
                }
                is GraphQLFieldDefinition -> {
                    sequenceOf(e.arguments)
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
                node.hasAppliedDirective(AliasDirective.name) ||
                    context.parentContext.parentNode
                        .toOption()
                        .filterIsInstance<GraphQLInterfaceType>()
                        .mapNotNull { git: GraphQLInterfaceType ->
                            git.getFieldDefinition(node.name)
                        }
                        .map { gfd: GraphQLFieldDefinition ->
                            gfd.getAppliedDirectives(AliasDirective.name)
                        }
                        .filter(List<GraphQLAppliedDirective>::isNotEmpty)
                        .isDefined()
            ) {
                updateAliasCoordinatesRegistryWithAliasForFieldDefinition(node, context)
            }
            return TraversalControl.CONTINUE
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>,
        ) {
            when (val grandParentNode = context.parentContext?.parentNode) {
                is GraphQLInterfaceType -> {
                    when (val parentNode = context.parentNode) {
                        is GraphQLObjectType -> {
                            val fcs: List<FieldCoordinates> =
                                if (grandParentNode.getFieldDefinition(node.name) != null) {
                                    listOf(
                                        FieldCoordinates.coordinates(
                                            grandParentNode.name,
                                            node.name
                                        ),
                                        FieldCoordinates.coordinates(parentNode.name, node.name)
                                    )
                                } else {
                                    listOf(FieldCoordinates.coordinates(parentNode.name, node.name))
                                }
                            grandParentNode
                                .getFieldDefinition(node.name)
                                .toOption()
                                .map { gfd: GraphQLFieldDefinition ->
                                    gfd.getAppliedDirectives(AliasDirective.name)
                                }
                                .filter(List<GraphQLAppliedDirective>::isNotEmpty)
                                .orElse {
                                    node.getAppliedDirectives(AliasDirective.name).toOption()
                                }
                                .map(List<GraphQLAppliedDirective>::asSequence)
                                .getOrElse { emptySequence() }
                                .fold(context.getCurrentAccumulate<AliasCoordinatesRegistry>()) {
                                    acr: AliasCoordinatesRegistry,
                                    gad: GraphQLAppliedDirective ->
                                    val aliasArgument: GraphQLAppliedDirectiveArgument =
                                        gad.getArgument(
                                            AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME
                                        )
                                    aliasArgument
                                        .getValue<String>()
                                        .toOption()
                                        .filter(String::isNotBlank)
                                        .map { alias: String ->
                                            fcs.fold(acr) {
                                                acr1: AliasCoordinatesRegistry,
                                                fc: FieldCoordinates ->
                                                acr1.registerFieldWithAlias(fc, alias)
                                            }
                                        }
                                        .getOrElse { acr }
                                }
                                .let { acr: AliasCoordinatesRegistry -> context.setAccumulate(acr) }
                        }
                    }
                }
                else -> {
                    when (val parentNode: GraphQLSchemaElement = context.parentNode) {
                        is GraphQLObjectType -> {
                            val fc: FieldCoordinates =
                                FieldCoordinates.coordinates(parentNode.name, node.name)
                            node
                                .getAppliedDirectives(AliasDirective.name)
                                .asSequence()
                                .fold(context.getCurrentAccumulate<AliasCoordinatesRegistry>()) {
                                    acr: AliasCoordinatesRegistry,
                                    gad: GraphQLAppliedDirective ->
                                    val aliasArgument: GraphQLAppliedDirectiveArgument =
                                        gad.getArgument(
                                            AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME
                                        )
                                    aliasArgument
                                        .getValue<String>()
                                        .toOption()
                                        .filter(String::isNotBlank)
                                        .map { alias: String ->
                                            acr.registerFieldWithAlias(fc, alias)
                                        }
                                        .getOrElse { acr }
                                }
                                .let { acr: AliasCoordinatesRegistry -> context.setAccumulate(acr) }
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
                node.hasAppliedDirective(AliasDirective.name) ||
                    context.parentContext
                        ?.parentContext
                        ?.parentNode
                        .toOption()
                        .filterIsInstance<GraphQLInterfaceType>()
                        .flatMap { git: GraphQLInterfaceType ->
                            context.parentNode
                                .toOption()
                                .filterIsInstance<GraphQLFieldDefinition>()
                                .mapNotNull { gfd: GraphQLFieldDefinition ->
                                    git.getFieldDefinition(gfd.name)
                                }
                                .mapNotNull { gfd: GraphQLFieldDefinition ->
                                    gfd.getArgument(node.name)
                                }
                                .map { ga: GraphQLArgument ->
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
                                    updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnGreatGrandParentInterface(
                                        greatGrandParentNode,
                                        grandParentNode,
                                        parentNode,
                                        node,
                                        context
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    when (val grandParentNode = context.parentContext?.parentNode) {
                        is GraphQLObjectType -> {
                            when (val parentNode = context.parentNode) {
                                is GraphQLFieldDefinition -> {
                                    updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnGrandParentObject(
                                        grandParentNode,
                                        parentNode,
                                        node,
                                        context
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnGreatGrandParentInterface(
            greatGrandParentNode: GraphQLInterfaceType,
            grandParentNode: GraphQLObjectType,
            parentNode: GraphQLFieldDefinition,
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>,
        ) {
            val fcLocs: List<Pair<FieldCoordinates, String>> =
                if (greatGrandParentNode.getFieldDefinition(parentNode.name) != null) {
                    listOf(
                        FieldCoordinates.coordinates(greatGrandParentNode.name, parentNode.name) to
                            node.name,
                        FieldCoordinates.coordinates(grandParentNode.name, parentNode.name) to
                            node.name
                    )
                } else {
                    listOf(
                        FieldCoordinates.coordinates(grandParentNode.name, parentNode.name) to
                            node.name
                    )
                }
            greatGrandParentNode
                .getFieldDefinition(parentNode.name)
                .toOption()
                .mapNotNull { gfd: GraphQLFieldDefinition -> gfd.getArgument(node.name) }
                .map { ga: GraphQLArgument -> ga.getAppliedDirectives(AliasDirective.name) }
                .filter(List<GraphQLAppliedDirective>::isNotEmpty)
                .orElse { node.getAppliedDirectives(AliasDirective.name).toOption() }
                .map(List<GraphQLAppliedDirective>::asSequence)
                .getOrElse { emptySequence() }
                .fold(context.getCurrentAccumulate<AliasCoordinatesRegistry>()) {
                    acr: AliasCoordinatesRegistry,
                    gad: GraphQLAppliedDirective ->
                    val aliasArgument: GraphQLAppliedDirectiveArgument =
                        gad.getArgument(AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME)
                    aliasArgument
                        .getValue<String>()
                        .toOption()
                        .filter(String::isNotBlank)
                        .map { alias: String ->
                            fcLocs.fold(acr) {
                                acr1: AliasCoordinatesRegistry,
                                faLoc: Pair<FieldCoordinates, String> ->
                                acr1.registerFieldArgumentWithAlias(faLoc, alias)
                            }
                        }
                        .getOrElse { acr }
                }
                .let { acr: AliasCoordinatesRegistry -> context.setAccumulate(acr) }
        }

        private fun updateAliasCoordinatesRegistryWithAliasForFieldArgumentOnGrandParentObject(
            grandParentNode: GraphQLObjectType,
            parentNode: GraphQLFieldDefinition,
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>,
        ) {
            val fcLocs: List<Pair<FieldCoordinates, String>> =
                listOf(
                    FieldCoordinates.coordinates(grandParentNode.name, parentNode.name) to node.name
                )
            node
                .getAppliedDirectives(AliasDirective.name)
                .asSequence()
                .fold(context.getCurrentAccumulate<AliasCoordinatesRegistry>()) {
                    acr: AliasCoordinatesRegistry,
                    gad: GraphQLAppliedDirective ->
                    val aliasArgument: GraphQLAppliedDirectiveArgument =
                        gad.getArgument(AliasDirective.NAME_INPUT_VALUE_DEFINITION_NAME)
                    aliasArgument
                        .getValue<String>()
                        .toOption()
                        .filter(String::isNotBlank)
                        .map { alias: String ->
                            fcLocs.fold(acr) {
                                acr1: AliasCoordinatesRegistry,
                                faLoc: Pair<FieldCoordinates, String> ->
                                acr1.registerFieldArgumentWithAlias(faLoc, alias)
                            }
                        }
                        .getOrElse { acr }
                }
                .let { acr: AliasCoordinatesRegistry -> context.setAccumulate(acr) }
        }
    }
}
