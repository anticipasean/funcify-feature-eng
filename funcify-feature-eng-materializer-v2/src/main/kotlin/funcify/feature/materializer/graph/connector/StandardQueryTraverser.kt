package funcify.feature.materializer.graph.connector

import arrow.core.None
import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.*
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger

internal class StandardQueryTraverser(
    private val queryComponentContextFactory: QueryComponentContextFactory,
    private val materializationMetamodel: MaterializationMetamodel
) : (String, Document) -> Iterable<QueryComponentContext> {

    companion object {
        private const val METHOD_TAG: String = "standard_query_traverser.invoke"

        private val logger: Logger = loggerFor<StandardQueryTraverser>()

        private data class StandardQueryTraversalContext(
            val queue: PersistentList<QueryComponentContext>
        )

        private class StandardQueryTraverserVisitor(private val nodeVisitor: NodeVisitor) :
            TraverserVisitor<Node<*>> {

            override fun enter(context: TraverserContext<Node<*>>): TraversalControl {
                return context.thisNode().accept(context, nodeVisitor)
            }

            override fun leave(context: TraverserContext<Node<*>>): TraversalControl {
                return TraversalControl.CONTINUE
            }
        }

        private class StandardQueryNodeVisitor(
            private val queryComponentContextFactory: QueryComponentContextFactory,
            private val materializationMetamodel: MaterializationMetamodel
        ) : NodeVisitorStub() {

            companion object {
                private val logger: Logger = loggerFor<StandardQueryNodeVisitor>()
            }

            override fun visitOperationDefinition(
                node: OperationDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_operation_definition: [ node.name: {} ]", node.name)
                return TraversalControl.CONTINUE
            }

            override fun visitField(
                node: Field,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug(
                    "visit_field: [ node.name: {}, node.alias: {}, node.resultKey: {} ]",
                    node.name,
                    node.alias,
                    node.resultKey
                )
                when (val parentNode: Node<*> = context.parentNode) {
                    is OperationDefinition -> {
                        val p: GQLOperationPath =
                            extractParentPathFromContext(context).transform {
                                when (node.alias) {
                                    null -> appendField(node.name)
                                    else -> appendAliasedField(node.alias, node.name)
                                }
                            }
                        val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
                        val fd: GraphQLFieldDefinition =
                            getGraphQLFieldDefinitionForField(
                                    materializationMetamodel.materializationGraphQLSchema.queryType,
                                    node
                                )
                                .orElseThrow()
                        val fc: FieldCoordinates =
                            FieldCoordinates.coordinates(
                                materializationMetamodel.materializationGraphQLSchema.queryType,
                                fd
                            )
                        val qcc: QueryComponentContext =
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .field(node)
                                .path(p)
                                .fieldCoordinates(fc)
                                .build()
                        context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                        context.setVar(GQLOperationPath::class.java, p)
                        context.setVar(FieldCoordinates::class.java, fc)
                        GraphQLTypeUtil.unwrapAll(fd.type)
                            .toOption()
                            .filterIsInstance<GraphQLCompositeType>()
                            .tap { gqlct: GraphQLCompositeType ->
                                context.setVar(GraphQLCompositeType::class.java, gqlct)
                            }
                    }
                    is Field -> {
                        val p: GQLOperationPath =
                            extractParentPathFromContext(context).transform {
                                when (node.alias) {
                                    null -> appendField(node.name)
                                    else -> appendAliasedField(node.alias, node.name)
                                }
                            }
                        val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
                        val gct: GraphQLCompositeType = getParentCompositeTypeFromContext(context)
                        val fd: GraphQLFieldDefinition =
                            getGraphQLFieldDefinitionForField(gct, node).orElseThrow()
                        val fc: FieldCoordinates = FieldCoordinates.coordinates(gct.name, fd.name)
                        val qcc: QueryComponentContext =
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .field(node)
                                .fieldCoordinates(fc)
                                .path(p)
                                .build()
                        context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                        context.setVar(GQLOperationPath::class.java, p)
                        context.setVar(FieldCoordinates::class.java, fc)
                        GraphQLTypeUtil.unwrapAll(fd.type)
                            .toOption()
                            .filterIsInstance<GraphQLCompositeType>()
                            .tap { gqlct: GraphQLCompositeType ->
                                context.setVar(GraphQLCompositeType::class.java, gqlct)
                            }
                    }
                    is InlineFragment -> {
                        val p: GQLOperationPath =
                            extractParentPathFromContext(context).transform {
                                when (node.alias) {
                                    null -> {
                                        when (parentNode.typeCondition) {
                                            null -> {
                                                appendField(node.name)
                                            }
                                            else -> {
                                                appendInlineFragment(
                                                    parentNode.typeCondition.name,
                                                    node.name
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        when (parentNode.typeCondition) {
                                            null -> {
                                                appendAliasedField(node.alias, node.name)
                                            }
                                            else -> {
                                                appendInlineFragment(
                                                    parentNode.typeCondition.name,
                                                    node.alias,
                                                    node.name
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
                        val gct: GraphQLCompositeType =
                            getParentCompositeTypeFromContext(context)
                                .toOption()
                                .filter { gct: GraphQLCompositeType ->
                                    gct.name == parentNode.typeCondition?.name
                                }
                                .orElse {
                                    parentNode.typeCondition
                                        .toOption()
                                        .mapNotNull { tn: TypeName ->
                                            materializationMetamodel.materializationGraphQLSchema
                                                .getType(tn.name)
                                        }
                                        .filterIsInstance<GraphQLCompositeType>()
                                }
                                .successIfDefined {
                                    ServiceError.of(
                                        "unable to determine parent composite_type for field [ name: %s ]",
                                        node.name
                                    )
                                }
                                .orElseThrow()
                        val fd: GraphQLFieldDefinition =
                            getGraphQLFieldDefinitionForField(gct, node).orElseThrow()
                        val fc: FieldCoordinates = FieldCoordinates.coordinates(gct.name, fd.name)
                        val qcc: QueryComponentContext =
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .field(node)
                                .path(p)
                                .fieldCoordinates(fc)
                                .build()
                        context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                        context.setVar(GQLOperationPath::class.java, p)
                        context.setVar(FieldCoordinates::class.java, fc)
                        GraphQLTypeUtil.unwrapAll(fd.type)
                            .toOption()
                            .filterIsInstance<GraphQLCompositeType>()
                            .tap { gqlct: GraphQLCompositeType ->
                                context.setVar(GraphQLCompositeType::class.java, gqlct)
                            }
                    }
                    is FragmentDefinition -> {
                        val p: GQLOperationPath =
                            extractParentPathFromContext(context).transform {
                                when (node.alias) {
                                    null -> {
                                        when (parentNode.typeCondition) {
                                            null -> {
                                                appendField(node.name)
                                            }
                                            else -> {
                                                appendFragmentSpread(
                                                    parentNode.name,
                                                    parentNode.typeCondition.name,
                                                    node.name
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        when (parentNode.typeCondition) {
                                            null -> {
                                                appendAliasedField(node.alias, node.name)
                                            }
                                            else -> {
                                                appendFragmentSpread(
                                                    parentNode.name,
                                                    parentNode.typeCondition.name,
                                                    node.alias,
                                                    node.name
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
                        val gct: GraphQLCompositeType =
                            getParentCompositeTypeFromContext(context)
                                .toOption()
                                .filter { gct: GraphQLCompositeType ->
                                    gct.name == parentNode.typeCondition?.name
                                }
                                .orElse {
                                    parentNode.typeCondition
                                        .toOption()
                                        .mapNotNull { tn: TypeName ->
                                            materializationMetamodel.materializationGraphQLSchema
                                                .getType(tn.name)
                                        }
                                        .filterIsInstance<GraphQLCompositeType>()
                                }
                                .successIfDefined {
                                    ServiceError.of(
                                        "unable to determine parent composite_type for field [ name: %s ]",
                                        node.name
                                    )
                                }
                                .orElseThrow()
                        val fd: GraphQLFieldDefinition =
                            getGraphQLFieldDefinitionForField(gct, node).orElseThrow()
                        val fc: FieldCoordinates = FieldCoordinates.coordinates(gct.name, fd.name)
                        val qcc: QueryComponentContext =
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .field(node)
                                .fieldCoordinates(fc)
                                .path(p)
                                .build()
                        context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                        context.setVar(GQLOperationPath::class.java, p)
                        context.setVar(FieldCoordinates::class.java, fc)
                        GraphQLTypeUtil.unwrapAll(fd.type)
                            .toOption()
                            .filterIsInstance<GraphQLCompositeType>()
                            .tap { gqlct: GraphQLCompositeType ->
                                context.setVar(GraphQLCompositeType::class.java, gqlct)
                            }
                    }
                }
                return TraversalControl.CONTINUE
            }

            private fun getGraphQLFieldDefinitionForField(
                parentCompositeType: GraphQLCompositeType,
                field: Field
            ): Try<GraphQLFieldDefinition> {
                return Try.attemptNullable {
                        Introspection.getFieldDef(
                            materializationMetamodel.materializationGraphQLSchema,
                            parentCompositeType,
                            field.name
                        )
                    }
                    .flatMap(Try.Companion::fromOption)
                    .mapFailure { t: Throwable ->
                        when (t) {
                            is ServiceError -> {
                                t
                            }
                            else -> {
                                ServiceError.builder()
                                    .message(
                                        "unable to get field_definition for field [ name: %s, alias: %s ]",
                                        field.name,
                                        field.alias
                                    )
                                    .cause(t)
                                    .build()
                            }
                        }
                    }
            }

            private fun getParentCompositeTypeFromContext(
                context: TraverserContext<Node<*>>
            ): GraphQLCompositeType {
                return try {
                        context.getVarFromParents(GraphQLCompositeType::class.java).toOption()
                    } catch (c: ClassCastException) {
                        None
                    }
                    .successIfDefined {
                        ServiceError.of("unable to get parent_type from traverser_context")
                    }
                    .orElseThrow()
            }

            override fun visitFragmentDefinition(
                node: FragmentDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_fragment_definition: [ node.name: {} ]", node.name)
                return TraversalControl.CONTINUE
            }

            override fun visitFragmentSpread(
                node: FragmentSpread,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_fragment_spread: [ node.name: {} ]", node.name)
                return TraversalControl.CONTINUE
            }

            override fun visitInlineFragment(
                node: InlineFragment,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug(
                    "visit_inline_fragment: [ node.type_condition: {} ]",
                    node.typeCondition
                )
                return TraversalControl.CONTINUE
            }

            override fun visitArgument(
                node: Argument,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_argument: [ node.name: {} ]", node.name)
                val p: GQLOperationPath =
                    extractParentPathFromContext(context).transform { argument(node.name) }
                val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
                val fc: FieldCoordinates = extractFieldCoordinatesFromContext(context)
                val qcc: QueryComponentContext =
                    queryComponentContextFactory
                        .fieldArgumentComponentContextBuilder()
                        .argument(node)
                        .path(p)
                        .fieldCoordinates(fc)
                        .build()
                context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                context.setVar(GQLOperationPath::class.java, p)
                return TraversalControl.CONTINUE
            }

            private fun extractFieldCoordinatesFromContext(
                context: TraverserContext<Node<*>>
            ): FieldCoordinates {
                return try {
                        context.getVarFromParents(FieldCoordinates::class.java).toOption()
                    } catch (c: ClassCastException) {
                        None
                    }
                    .successIfDefined {
                        ServiceError.of("field_coordinates not available in traverser_context")
                    }
                    .orElseThrow()
            }

            private fun extractParentPathFromContext(
                context: TraverserContext<Node<*>>
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
                        ServiceError.of(
                            "parent_path has not been set as variable in traverser_context"
                        )
                    }
            }
        }
    }

    override fun invoke(
        operationName: String,
        document: Document
    ): Iterable<QueryComponentContext> {
        logger.debug(
            "{}: [ operation_name: {}, document.operation_definition.selection_set.selections.size: {} ]",
            METHOD_TAG,
            operationName,
            document
                .getOperationDefinition(operationName)
                .toOption()
                .mapNotNull(OperationDefinition::getSelectionSet)
                .mapNotNull(SelectionSet::getSelections)
                .fold(::emptyList, ::identity)
                .size
        )
        return document
            .getOperationDefinition(operationName)
            .toOption()
            .map { od: OperationDefinition ->
                Traverser.breadthFirst(
                        nodeTraversalFunctionOverDocument(document),
                        GQLOperationPath.getRootPath(),
                        StandardQueryTraversalContext(queue = persistentListOf())
                    )
                    .traverse(
                        od,
                        StandardQueryTraverserVisitor(
                            nodeVisitor =
                                StandardQueryNodeVisitor(
                                    queryComponentContextFactory = queryComponentContextFactory,
                                    materializationMetamodel = materializationMetamodel
                                )
                        )
                    )
                    .toOption()
                    .mapNotNull(TraverserResult::getAccumulatedResult)
                    .filterIsInstance<StandardQueryTraversalContext>()
                    .map { c: StandardQueryTraversalContext -> c.queue }
                    .fold(::persistentListOf, ::identity)
            }
            .fold(::persistentListOf, ::identity)
    }

    private fun nodeTraversalFunctionOverDocument(document: Document): (Node<*>) -> List<Node<*>> {
        val fragmentDefinitionsByName: PersistentMap<String, FragmentDefinition> by lazy {
            document
                .getDefinitionsOfType(FragmentDefinition::class.java)
                .asSequence()
                .map { fd: FragmentDefinition -> fd.name to fd }
                .reducePairsToPersistentMap()
        }
        return { n: Node<*> ->
            when (n) {
                is OperationDefinition -> {
                    n.selectionSet
                        .toOption()
                        .mapNotNull(SelectionSet::getSelections)
                        .fold(::emptyList, ::identity)
                }
                is Field -> {
                    n.arguments
                        .asSequence()
                        .plus(
                            n.selectionSet
                                .toOption()
                                .mapNotNull(SelectionSet::getSelections)
                                .fold(::emptySequence, List<Selection<*>>::asSequence)
                        )
                        .toList()
                }
                is InlineFragment -> {
                    n.selectionSet
                        .toOption()
                        .mapNotNull(SelectionSet::getSelections)
                        .fold(::emptyList, ::identity)
                }
                is FragmentSpread -> {
                    fragmentDefinitionsByName.getOrNone(n.name).fold(::emptyList, ::listOf)
                }
                is FragmentDefinition -> {
                    n.selectionSet
                        .toOption()
                        .mapNotNull(SelectionSet::getSelections)
                        .fold(::emptyList, ::identity)
                }
                else -> {
                    emptyList()
                }
            }
        }
    }
}
