package funcify.feature.schema.directive.temporal

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.lastOrNone
import arrow.core.toOption
import funcify.feature.directive.LastUpdatedDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.limit.ModelLimits
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.introspection.Introspection
import graphql.scalars.ExtendedScalars
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.GraphQLTypeVisitorStub
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import java.util.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

object LastUpdatedCoordinatesRegistryCreator {

    private const val METHOD_TAG: String = "create_last_updated_coordinates_registry_for"
    private val logger: Logger = loggerFor<LastUpdatedCoordinatesRegistryCreator>()

    fun createLastUpdatedCoordinatesRegistryFor(
        modelLimits: ModelLimits,
        graphQLSchema: GraphQLSchema,
        path: GQLOperationPath,
        fieldCoordinates: FieldCoordinates
    ): Try<LastUpdatedCoordinatesRegistry> {
        logger.info("{}: [ path: {}, field_coordinates: {} ]", METHOD_TAG, path, fieldCoordinates)
        return findGraphQLFieldsContainerCorrespondingToFieldCoordinates(
                graphQLSchema,
                fieldCoordinates
            )
            .flatMap { gfc: GraphQLFieldsContainer ->
                findGraphQLFieldDefinitionForContainerAndCoordinates(
                    graphQLSchema,
                    gfc,
                    fieldCoordinates
                )
            }
            .flatMap { gfd: GraphQLFieldDefinition ->
                gfd.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapAll)
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .successIfDefined {
                        ServiceError.of(
                            "%s does not have type that unwraps to %s",
                            GraphQLFieldDefinition::class.simpleName,
                            GraphQLFieldsContainer::class.simpleName
                        )
                    }
            }
            .zip(assessWhetherPathCorrespondsToFieldCoordinates(path, fieldCoordinates))
            .flatMap { (gfc: GraphQLFieldsContainer, p: GQLOperationPath) ->
                traverseContainerLookingForLastUpdatedDirectiveAnnotatedElements(
                    modelLimits,
                    graphQLSchema,
                    p,
                    gfc
                )
            }
            .map(LastUpdatedCoordinatesContext::lastUpdatedCoordinatesByPath)
            .map(::DefaultLastUpdatedCoordinatesRegistry)
    }

    private fun findGraphQLFieldsContainerCorrespondingToFieldCoordinates(
        graphQLSchema: GraphQLSchema,
        fieldCoordinates: FieldCoordinates,
    ): Try<GraphQLFieldsContainer> {
        return graphQLSchema
            .getType(fieldCoordinates.typeName)
            .toOption()
            .filterIsInstance<GraphQLFieldsContainer>()
            .successIfDefined {
                ServiceError.of(
                    "%s not found for type_name in [ field_coordinates: %s ]",
                    GraphQLFieldsContainer::class.simpleName,
                    fieldCoordinates
                )
            }
    }

    private fun findGraphQLFieldDefinitionForContainerAndCoordinates(
        graphQLSchema: GraphQLSchema,
        graphQLFieldsContainer: GraphQLFieldsContainer,
        fieldCoordinates: FieldCoordinates,
    ): Try<GraphQLFieldDefinition> {
        return Try.attemptNullable({
            Introspection.getFieldDef(
                graphQLSchema,
                graphQLFieldsContainer,
                fieldCoordinates.fieldName
            )
        }) {
            ServiceError.of(
                "%s not found for field_coordinates [ %s ]",
                GraphQLFieldDefinition::class.simpleName,
                fieldCoordinates
            )
        }
    }

    private fun assessWhetherPathCorrespondsToFieldCoordinates(
        path: GQLOperationPath,
        fieldCoordinates: FieldCoordinates,
    ): Try<GQLOperationPath> {
        return Try.success(path)
            .filter(GQLOperationPath::refersToSelection) { p: GQLOperationPath ->
                ServiceError.of("path [ %s ] does not refer to field selection", p)
            }
            .filter({ p: GQLOperationPath ->
                p.selection
                    .lastOrNone()
                    .filter { ss: SelectionSegment ->
                        when (ss) {
                            is AliasedFieldSegment -> {
                                ss.fieldName == fieldCoordinates.fieldName
                            }
                            is FieldSegment -> {
                                ss.fieldName == fieldCoordinates.fieldName
                            }
                            is InlineFragmentSegment -> {
                                ss.typeName == fieldCoordinates.typeName &&
                                    ss.selectedField.fieldName == fieldCoordinates.fieldName
                            }
                            is FragmentSpreadSegment -> {
                                false
                            }
                        }
                    }
                    .isDefined()
            }) { p: GQLOperationPath ->
                ServiceError.of(
                    "path [ %s ] does not correspond to field_coordinates [ %s ]",
                    p,
                    fieldCoordinates
                )
            }
    }

    private fun traverseContainerLookingForLastUpdatedDirectiveAnnotatedElements(
        modelLimits: ModelLimits,
        graphQLSchema: GraphQLSchema,
        path: GQLOperationPath,
        graphQLFieldsContainer: GraphQLFieldsContainer,
    ): Try<LastUpdatedCoordinatesContext> {
        val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>> = LinkedList()
        return Traverser.breadthFirst<GraphQLSchemaElement>(
                nodeTraversalFunction(graphQLSchema),
                path,
                LastUpdatedCoordinatesContext(lastUpdatedCoordinatesByPath = persistentMapOf())
            )
            .traverse(
                graphQLFieldsContainer,
                LastUpdatedCoordinatesTraverserVisitor(
                    LastUpdatedCoordinatesVisitor(backRefQueue = backRefQueue)
                )
            )
            .successIfNonNull {
                ServiceError.of("%s not returned for traversal", TraverserResult::class.simpleName)
            }
            .map(TraverserResult::getAccumulatedResult)
            .filterInstanceOf<LastUpdatedCoordinatesContext>()
            .map(
                gatherLastUpdatedCoordinatesUntilMaximumOperationDepth(
                    modelLimits,
                    graphQLSchema,
                    backRefQueue
                )
            )
    }

    private fun nodeTraversalFunction(
        graphQLSchema: GraphQLSchema
    ): (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLInterfaceType -> {
                    graphQLSchema.getImplementations(e)
                }
                is GraphQLObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLFieldDefinition -> {
                    e.type
                        .toOption()
                        .mapNotNull(GraphQLTypeUtil::unwrapAll)
                        .filterIsInstance<GraphQLFieldsContainer>()
                        .fold(::emptyList, ::listOf)
                }
                else -> {
                    emptyList<GraphQLSchemaElement>()
                }
            }
        }
    }

    private fun gatherLastUpdatedCoordinatesUntilMaximumOperationDepth(
        modelLimits: ModelLimits,
        graphQLSchema: GraphQLSchema,
        backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>
    ): (LastUpdatedCoordinatesContext) -> LastUpdatedCoordinatesContext {
        return { context: LastUpdatedCoordinatesContext ->
            var c: LastUpdatedCoordinatesContext = context
            while (
                backRefQueue.isNotEmpty() &&
                    backRefQueue.peekFirst().first.level() < modelLimits.maximumOperationDepth
            ) {
                val (p: GQLOperationPath, gfc: GraphQLFieldsContainer) = backRefQueue.pollFirst()
                c =
                    Traverser.breadthFirst(nodeTraversalFunction(graphQLSchema), p, c)
                        .traverse(
                            gfc,
                            LastUpdatedCoordinatesTraverserVisitor(
                                LastUpdatedCoordinatesVisitor(backRefQueue)
                            )
                        )
                        .successIfNonNull {
                            ServiceError.of(
                                "%s not returned for traversal",
                                TraverserResult::class.simpleName
                            )
                        }
                        .map(TraverserResult::getAccumulatedResult)
                        .filterInstanceOf<LastUpdatedCoordinatesContext>()
                        .orElseThrow()
            }
            c
        }
    }

    private data class LastUpdatedCoordinatesContext(
        val lastUpdatedCoordinatesByPath:
            PersistentMap<GQLOperationPath, PersistentSet<FieldCoordinates>>
    )

    private class LastUpdatedCoordinatesTraverserVisitor(
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

    private class LastUpdatedCoordinatesVisitor(
        private val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>
    ) : GraphQLTypeVisitorStub() {

        companion object {
            private val logger: Logger = loggerFor<LastUpdatedCoordinatesVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_field_definition: [ node.name: {} ]", node.name)
            if (
                node.hasAppliedDirective(LastUpdatedDirective.name) ||
                    context.parentContext
                        ?.parentNode
                        .toOption()
                        .filterIsInstance<GraphQLInterfaceType>()
                        .mapNotNull { git: GraphQLInterfaceType ->
                            git.getFieldDefinition(node.name)
                        }
                        .filter { gfd: GraphQLFieldDefinition ->
                            gfd.hasAppliedDirective(LastUpdatedDirective.name)
                        }
                        .isDefined()
            ) {
                addLastUpdatedAnnotatedElementToRegistry(node, context)
            } else {
                val p: GQLOperationPath = createPathFromContext(node, context)
                context.setVar(GQLOperationPath::class.java, p)
            }
            return TraversalControl.CONTINUE
        }

        private fun addLastUpdatedAnnotatedElementToRegistry(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ) {
            when {
                !node.type
                    .toOption()
                    .map(GraphQLTypeUtil::unwrapNonNull)
                    .filterIsInstance<GraphQLScalarType>()
                    .filter { gst: GraphQLScalarType ->
                        sequenceOf(ExtendedScalars.Date, ExtendedScalars.DateTime)
                            .map(GraphQLScalarType::getName)
                            .any(gst.name::equals)
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        """field_definition [ node.name: %s ] 
                            |annotated with schema directive [ %s ] 
                            |not of temporal type [ %s ]"""
                            .flatten(),
                        node.name,
                        LastUpdatedDirective.name,
                        GraphQLTypeUtil.simplePrint(node.type)
                    )
                }
                context.parentContext
                    ?.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLInterfaceType>()
                    .filter { git: GraphQLInterfaceType ->
                        git.fieldDefinitions
                            .asSequence()
                            .filter { gfd: GraphQLFieldDefinition ->
                                gfd.hasAppliedDirective(LastUpdatedDirective.name)
                            }
                            .count() > 1
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        "more than one field on %s [ name: %s ] is annotated with @%s directive",
                        GraphQLInterfaceType::class.simpleName,
                        context.parentContext
                            ?.parentNode
                            .toOption()
                            .filterIsInstance<GraphQLInterfaceType>()
                            .map(GraphQLInterfaceType::getName)
                            .getOrElse { "<NA>" },
                        LastUpdatedDirective.name
                    )
                }
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLObjectType>()
                    .filter { got: GraphQLObjectType ->
                        got.fieldDefinitions
                            .asSequence()
                            .filter { gfd: GraphQLFieldDefinition ->
                                gfd.hasAppliedDirective(LastUpdatedDirective.name)
                            }
                            .count() > 1
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        "more than one field on %s [ name: %s ] is annotated with @%s directive",
                        GraphQLObjectType::class.simpleName,
                        context.parentNode
                            .toOption()
                            .filterIsInstance<GraphQLObjectType>()
                            .map(GraphQLObjectType::getName)
                            .getOrElse { "<NA>" },
                        LastUpdatedDirective.name
                    )
                }
                else -> {
                    val p: GQLOperationPath = createPathFromContext(node, context)
                    context.setVar(GQLOperationPath::class.java, p)
                    val c: LastUpdatedCoordinatesContext = context.getCurrentAccumulate()
                    val fcs: Sequence<FieldCoordinates> =
                        context.parentContext
                            ?.parentNode
                            .toOption()
                            .filterIsInstance<GraphQLInterfaceType>()
                            .filter { git: GraphQLInterfaceType ->
                                git.getFieldDefinition(node.name)
                                    .toOption()
                                    .filter { gfd: GraphQLFieldDefinition ->
                                        gfd.hasAppliedDirective(LastUpdatedDirective.name)
                                    }
                                    .isDefined()
                            }
                            .fold(::emptySequence, ::sequenceOf)
                            .plus(
                                context.parentNode
                                    .toOption()
                                    .filterIsInstance<GraphQLObjectType>()
                                    .filter { got: GraphQLObjectType ->
                                        got.getFieldDefinition(node.name) != null
                                    }
                                    .fold(::emptySequence, ::sequenceOf)
                            )
                            .map { gfc: GraphQLFieldsContainer ->
                                FieldCoordinates.coordinates(gfc, node.name)
                            }
                    context.setAccumulate(
                        c.copy(
                            lastUpdatedCoordinatesByPath =
                                fcs.fold(c.lastUpdatedCoordinatesByPath) { lucp, fc ->
                                    lucp.put(p, lucp.getOrElse(p, ::persistentSetOf).add(fc))
                                }
                        )
                    )
                }
            }
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
