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
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
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
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal object StandardQueryTraverser : (StandardQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "standard_query_traverser.invoke"

    private val logger: Logger = loggerFor<StandardQueryTraverser>()

    private data class StandardQueryTraversalContext(
        val queue: PersistentList<QueryComponentContext>
    )

    private data class StandardQueryPathContext(
        val path: GQLOperationPath,
        val canonicalPath: GQLOperationPath
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

        override fun visitField(node: Field, context: TraverserContext<Node<*>>): TraversalControl {
            logger.debug(
                "visit_field: [ node.name: {}, node.alias: {}, node.resultKey: {} ]",
                node.name,
                node.alias,
                node.resultKey
            )
            when (val parentNode: Node<*> = context.parentNode) {
                is OperationDefinition -> {
                    val pc: StandardQueryPathContext =
                        extractParentPathContextFromTraverserContext(context)
                    val cp: GQLOperationPath = pc.canonicalPath.transform { appendField(node.name) }
                    val p: GQLOperationPath =
                        pc.path.transform {
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
                            .fieldComponentContextBuilder()
                            .field(node)
                            .path(p)
                            .fieldCoordinates(fc)
                            .canonicalPath(cp)
                            .build()
                    context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                    context.setVar(
                        StandardQueryPathContext::class.java,
                        StandardQueryPathContext(p, cp)
                    )
                    context.setVar(FieldCoordinates::class.java, fc)
                    GraphQLTypeUtil.unwrapAll(fd.type)
                        .toOption()
                        .filterIsInstance<GraphQLCompositeType>()
                        .tap { gqlct: GraphQLCompositeType ->
                            context.setVar(GraphQLCompositeType::class.java, gqlct)
                        }
                }
                is Field -> {
                    val pc: StandardQueryPathContext =
                        extractParentPathContextFromTraverserContext(context)
                    val cp: GQLOperationPath = pc.canonicalPath.transform { appendField(node.name) }
                    val p: GQLOperationPath =
                        pc.path.transform {
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
                            .fieldComponentContextBuilder()
                            .field(node)
                            .fieldCoordinates(fc)
                            .path(p)
                            .canonicalPath(cp)
                            .build()
                    context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                    context.setVar(
                        StandardQueryPathContext::class.java,
                        StandardQueryPathContext(p, cp)
                    )
                    context.setVar(FieldCoordinates::class.java, fc)
                    GraphQLTypeUtil.unwrapAll(fd.type)
                        .toOption()
                        .filterIsInstance<GraphQLCompositeType>()
                        .tap { gqlct: GraphQLCompositeType ->
                            context.setVar(GraphQLCompositeType::class.java, gqlct)
                        }
                }
                is InlineFragment -> {
                    val pc: StandardQueryPathContext =
                        extractParentPathContextFromTraverserContext(context)
                    val p: GQLOperationPath =
                        pc.path.transform {
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
                    val cp: GQLOperationPath =
                        pc.canonicalPath.transform {
                            when (parentNode.typeCondition) {
                                null -> {
                                    appendField(node.name)
                                }
                                else -> {
                                    if (gct.name != parentNode.typeCondition.name) {
                                        appendInlineFragment(
                                            parentNode.typeCondition.name,
                                            node.name
                                        )
                                    } else {
                                        appendField(node.name)
                                    }
                                }
                            }
                        }
                    val fc: FieldCoordinates = FieldCoordinates.coordinates(gct.name, fd.name)
                    val qcc: QueryComponentContext =
                        queryComponentContextFactory
                            .fieldComponentContextBuilder()
                            .field(node)
                            .path(p)
                            .fieldCoordinates(fc)
                            .canonicalPath(cp)
                            .build()
                    context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                    context.setVar(
                        StandardQueryPathContext::class.java,
                        StandardQueryPathContext(p, cp)
                    )
                    context.setVar(FieldCoordinates::class.java, fc)
                    GraphQLTypeUtil.unwrapAll(fd.type)
                        .toOption()
                        .filterIsInstance<GraphQLCompositeType>()
                        .tap { gqlct: GraphQLCompositeType ->
                            context.setVar(GraphQLCompositeType::class.java, gqlct)
                        }
                }
                is FragmentDefinition -> {
                    val pc: StandardQueryPathContext =
                        extractParentPathContextFromTraverserContext(context)
                    val p: GQLOperationPath =
                        pc.path.transform {
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
                    val cp: GQLOperationPath =
                        pc.canonicalPath.transform {
                            when (parentNode.typeCondition) {
                                null -> {
                                    appendField(node.name)
                                }
                                else -> {
                                    if (gct.name != parentNode.typeCondition.name) {
                                        appendInlineFragment(
                                            parentNode.typeCondition.name,
                                            node.name
                                        )
                                    } else {
                                        appendField(node.name)
                                    }
                                }
                            }
                        }
                    val fd: GraphQLFieldDefinition =
                        getGraphQLFieldDefinitionForField(gct, node).orElseThrow()
                    val fc: FieldCoordinates = FieldCoordinates.coordinates(gct.name, fd.name)
                    val qcc: QueryComponentContext =
                        queryComponentContextFactory
                            .fieldComponentContextBuilder()
                            .field(node)
                            .fieldCoordinates(fc)
                            .path(p)
                            .canonicalPath(cp)
                            .build()
                    context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
                    context.setVar(
                        StandardQueryPathContext::class.java,
                        StandardQueryPathContext(p, cp)
                    )
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
            logger.debug("visit_inline_fragment: [ node.type_condition: {} ]", node.typeCondition)
            return TraversalControl.CONTINUE
        }

        override fun visitArgument(
            node: Argument,
            context: TraverserContext<Node<*>>
        ): TraversalControl {
            logger.debug("visit_argument: [ node.name: {} ]", node.name)
            val pc: StandardQueryPathContext = extractParentPathContextFromTraverserContext(context)
            val p: GQLOperationPath = pc.path.transform { argument(node.name) }
            val cp: GQLOperationPath = pc.canonicalPath.transform { argument(node.name) }
            val c: StandardQueryTraversalContext = context.getCurrentAccumulate()
            val fc: FieldCoordinates = extractFieldCoordinatesFromContext(context)
            val qcc: QueryComponentContext =
                queryComponentContextFactory
                    .argumentComponentContextBuilder()
                    .argument(node)
                    .path(p)
                    .fieldCoordinates(fc)
                    .canonicalPath(cp)
                    .build()
            context.setAccumulate(c.copy(queue = c.queue.add(qcc)))
            context.setVar(StandardQueryPathContext::class.java, StandardQueryPathContext(p, cp))
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

        private fun extractParentPathContextFromTraverserContext(
            context: TraverserContext<Node<*>>
        ): StandardQueryPathContext {
            return try {
                    context
                        .getVarFromParents<StandardQueryPathContext>(
                            StandardQueryPathContext::class.java
                        )
                        .toOption()
                } catch (c: ClassCastException) {
                    None
                }
                .orElse {
                    try {
                        context.getSharedContextData<StandardQueryPathContext>().toOption()
                    } catch (c: ClassCastException) {
                        None
                    }
                }
                .successIfDefined {
                    ServiceError.of(
                        "parent_path_context has not been set as variable in traverser_context"
                    )
                }
                .orElseThrow()
        }
    }

    override fun invoke(standardQuery: StandardQuery): Iterable<QueryComponentContext> {
        logger.debug(
            "{}: [ operation_name: {}, document.operation_definition.selection_set.selections.size: {} ]",
            METHOD_TAG,
            standardQuery.operationName,
            standardQuery.document.definitions
                .asSequence()
                .filterIsInstance<OperationDefinition>()
                .filter { od: OperationDefinition ->
                    if (standardQuery.operationName.isNotBlank()) {
                        od.name == standardQuery.operationName
                    } else {
                        true
                    }
                }
                .firstOrNone()
                .mapNotNull(OperationDefinition::getSelectionSet)
                .mapNotNull(SelectionSet::getSelections)
                .fold(::emptyList, ::identity)
                .size
        )
        return standardQuery.document.definitions
            .asSequence()
            .filterIsInstance<OperationDefinition>()
            .filter { od: OperationDefinition ->
                if (standardQuery.operationName.isNotBlank()) {
                    od.name == standardQuery.operationName
                } else {
                    true
                }
            }
            .firstOrNone()
            .map { od: OperationDefinition ->
                Traverser.depthFirst(
                        nodeTraversalFunctionOverDocument(
                            standardQuery.document,
                            standardQuery.materializationMetamodel
                        ),
                        StandardQueryPathContext(
                            path = GQLOperationPath.getRootPath(),
                            canonicalPath = GQLOperationPath.getRootPath()
                        ),
                        StandardQueryTraversalContext(queue = persistentListOf())
                    )
                    .traverse(
                        od,
                        StandardQueryTraverserVisitor(
                            nodeVisitor =
                                StandardQueryNodeVisitor(
                                    queryComponentContextFactory =
                                        standardQuery.queryComponentContextFactory,
                                    materializationMetamodel =
                                        standardQuery.materializationMetamodel
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

    private fun nodeTraversalFunctionOverDocument(
        document: Document,
        materializationMetamodel: MaterializationMetamodel
    ): (Node<*>) -> List<Node<*>> {
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
                        .sortedWith(
                            featuresLastComparator(
                                fragmentDefinitionsByName,
                                materializationMetamodel
                            )
                        )
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

    private fun featuresLastComparator(
        fragmentDefinitionsByName: PersistentMap<String, FragmentDefinition>,
        materializationMetamodel: MaterializationMetamodel
    ): Comparator<Selection<*>> {
        val priorityByElementTypeName: ImmutableMap<String, Int> =
            persistentMapOf(
                materializationMetamodel.featureEngineeringModel.transformerFieldCoordinates
                    .fieldName to 1,
                materializationMetamodel.featureEngineeringModel.dataElementFieldCoordinates
                    .fieldName to 2,
                materializationMetamodel.featureEngineeringModel.featureFieldCoordinates
                    .fieldName to 3
            )
        val selectionToPriorityMapper: (Selection<*>) -> Int = { s: Selection<*> ->
            when (s) {
                is Field -> {
                    priorityByElementTypeName[s.name] ?: 0
                }
                is InlineFragment -> {
                    // Get the lowest priority value for all fields under Query within this
                    // InlineFragment
                    s.toOption()
                        .mapNotNull(InlineFragment::getSelectionSet)
                        .mapNotNull(SelectionSet::getSelections)
                        .map(List<Selection<*>>::asSequence)
                        .fold(::emptySequence, ::identity)
                        .filterIsInstance<Field>()
                        .mapNotNull(Field::getName)
                        .mapNotNull { fn: String -> priorityByElementTypeName[fn] }
                        .minOrNull() ?: 0
                }
                is FragmentSpread -> {
                    s.toOption()
                        .mapNotNull { fs: FragmentSpread -> fragmentDefinitionsByName[fs.name] }
                        .mapNotNull(FragmentDefinition::getSelectionSet)
                        .mapNotNull(SelectionSet::getSelections)
                        .map(List<Selection<*>>::asSequence)
                        .fold(::emptySequence, ::identity)
                        .filterIsInstance<Field>()
                        .mapNotNull(Field::getName)
                        .mapNotNull { fn: String -> priorityByElementTypeName[fn] }
                        .minOrNull() ?: 0
                }
                else -> {
                    0
                }
            }
        }
        return Comparator { s1: Selection<*>, s2: Selection<*> ->
            selectionToPriorityMapper(s1) - selectionToPriorityMapper(s2)
        }
    }
}
