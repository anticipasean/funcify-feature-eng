package funcify.feature.materializer.graph.connector

import arrow.core.Either
import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.recurseDepthFirst
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.*
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal object LazyStandardQueryTraverser : (StandardQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "lazy_standard_query_traverser.invoke"

    private val logger: Logger = loggerFor<LazyStandardQueryTraverser>()

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
                val fragmentDefinitionsByName: PersistentMap<String, FragmentDefinition> =
                    standardQuery.document
                        .getDefinitionsOfType(FragmentDefinition::class.java)
                        .asSequence()
                        .map { fd: FragmentDefinition -> fd.name to fd }
                        .reducePairsToPersistentMap()
                createQueryComponentContextsFromOperationDefinition(
                    standardQuery.materializationMetamodel,
                    standardQuery.queryComponentContextFactory,
                    fragmentDefinitionsByName,
                    od
                )
            }
            .fold(::emptySequence, ::identity)
            .asIterable()
    }

    private fun createQueryComponentContextsFromOperationDefinition(
        materializationMetamodel: MaterializationMetamodel,
        queryComponentContextFactory: QueryComponentContextFactory,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
        operationDefinition: OperationDefinition
    ): Sequence<QueryComponentContext> {
        return operationDefinition.selectionSet
            .toOption()
            .mapNotNull(SelectionSet::getSelections)
            .fold(::emptySequence, List<Selection<*>>::asSequence)
            .sortedWith(featuresLastComparator(fragmentDefinitionsByName, materializationMetamodel))
            .flatMap { s: Selection<*> ->
                createQueryComponentContextsForSelectionOnElementTypeLevel(
                    materializationMetamodel,
                    queryComponentContextFactory,
                    fragmentDefinitionsByName,
                    s
                )
            }
            .recurseDepthFirst(
                traverseFieldItsArgumentsAndDescendents(
                    materializationMetamodel,
                    queryComponentContextFactory,
                    fragmentDefinitionsByName
                )
            )
    }

    private fun createQueryComponentContextsForSelectionOnElementTypeLevel(
        materializationMetamodel: MaterializationMetamodel,
        queryComponentContextFactory: QueryComponentContextFactory,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
        selection: Selection<*>,
    ): Sequence<QueryComponentContext> {
        return sequenceOf(none<Selection<*>>() to selection).recurseDepthFirst {
            (parentSelection: Option<Selection<*>>, s: Selection<*>) ->
            when (s) {
                is Field -> {
                    val gqlfd: GraphQLFieldDefinition =
                        getGraphQLFieldDefinitionForField(
                                materializationMetamodel,
                                materializationMetamodel.materializationGraphQLSchema.queryType,
                                s.name
                            )
                            .orElseThrow()
                    val fc: FieldCoordinates =
                        FieldCoordinates.coordinates(
                            materializationMetamodel.materializationGraphQLSchema.queryType,
                            gqlfd
                        )
                    val p: GQLOperationPath =
                        calculatePathForChildFieldOnParentSelection(
                            none(),
                            parentSelection,
                            s,
                            fc,
                            fragmentDefinitionsByName
                        )
                    val cp: GQLOperationPath =
                        calculateCanonicalPathForChildFieldOnParentSelection(
                            none(),
                            parentSelection,
                            s,
                            fc,
                            fragmentDefinitionsByName
                        )
                    sequenceOf(
                        queryComponentContextFactory
                            .fieldComponentContextBuilder()
                            .field(s)
                            .fieldCoordinates(fc)
                            .path(p)
                            .canonicalPath(cp)
                            .build()
                            .right()
                    )
                }
                is InlineFragment -> {
                    s.selectionSet
                        .toOption()
                        .mapNotNull(SelectionSet::getSelections)
                        .fold(::emptySequence, List<Selection<*>>::asSequence)
                        .map { cs: Selection<*> -> (s.some() to cs).left() }
                }
                is FragmentSpread -> {
                    fragmentDefinitionsByName
                        .getOrNone(s.name)
                        .mapNotNull(FragmentDefinition::getSelectionSet)
                        .mapNotNull(SelectionSet::getSelections)
                        .fold(::emptySequence, List<Selection<*>>::asSequence)
                        .map { cs: Selection<*> -> (s.some() to cs).left() }
                }
                else -> {
                    emptySequence()
                }
            }
        }
    }

    private fun calculatePathForChildFieldOnParentSelection(
        parentPath: Option<GQLOperationPath>,
        parentSelection: Option<Selection<*>>,
        childField: Field,
        childFieldCoordinates: FieldCoordinates,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
    ): GQLOperationPath {
        return parentPath
            .getOrElse { GQLOperationPath.getRootPath() }
            .transform {
                when (val ps: Selection<*>? = parentSelection.orNull()) {
                    null -> {
                        when (childField.alias) {
                            null -> {
                                appendField(childField.name)
                            }
                            else -> {
                                appendAliasedField(childField.alias, childField.name)
                            }
                        }
                    }
                    is InlineFragment -> {
                        when (val tn: String? = ps.typeCondition?.name) {
                            null -> {
                                when (childField.alias) {
                                    null -> {
                                        appendField(childField.name)
                                    }
                                    else -> {
                                        appendAliasedField(childField.alias, childField.name)
                                    }
                                }
                            }
                            childFieldCoordinates.typeName -> {
                                when (childField.alias) {
                                    null -> {
                                        appendField(childField.name)
                                    }
                                    else -> {
                                        appendAliasedField(childField.alias, childField.name)
                                    }
                                }
                            }
                            else -> {
                                when (childField.alias) {
                                    null -> {
                                        appendInlineFragment(tn, childField.name)
                                    }
                                    else -> {
                                        appendInlineFragment(tn, childField.alias, childField.name)
                                    }
                                }
                            }
                        }
                    }
                    is FragmentSpread -> {
                        when (
                            val tn: String? =
                                fragmentDefinitionsByName.get(ps.name)?.typeCondition?.name
                        ) {
                            null -> {
                                when (childField.alias) {
                                    null -> {
                                        appendField(childField.name)
                                    }
                                    else -> {
                                        appendAliasedField(childField.alias, childField.name)
                                    }
                                }
                            }
                            childFieldCoordinates.typeName -> {
                                when (childField.alias) {
                                    null -> {
                                        appendField(childField.name)
                                    }
                                    else -> {
                                        appendAliasedField(childField.alias, childField.name)
                                    }
                                }
                            }
                            else -> {
                                when (childField.alias) {
                                    null -> {
                                        appendFragmentSpread(ps.name, tn, childField.name)
                                    }
                                    else -> {
                                        appendFragmentSpread(
                                            ps.name,
                                            tn,
                                            childField.alias,
                                            childField.name
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        throw ServiceError.of(
                            "unsupported selection type [ type: %s ]",
                            ps::class.simpleName
                        )
                    }
                }
            }
    }

    private fun calculateCanonicalPathForChildFieldOnParentSelection(
        parentPath: Option<GQLOperationPath>,
        parentSelection: Option<Selection<*>>,
        childField: Field,
        childFieldCoordinates: FieldCoordinates,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
    ): GQLOperationPath {
        return parentPath
            .getOrElse { GQLOperationPath.getRootPath() }
            .transform {
                when (val ps: Selection<*>? = parentSelection.orNull()) {
                    null -> {
                        appendField(childField.name)
                    }
                    is InlineFragment -> {
                        when (val tn: String? = ps.typeCondition?.name) {
                            null -> {
                                appendField(childField.name)
                            }
                            childFieldCoordinates.typeName -> {
                                appendField(childField.name)
                            }
                            else -> {
                                appendInlineFragment(tn, childField.name)
                            }
                        }
                    }
                    is FragmentSpread -> {
                        when (
                            val tn: String? =
                                fragmentDefinitionsByName.get(ps.name)?.typeCondition?.name
                        ) {
                            null -> {
                                appendField(childField.name)
                            }
                            childFieldCoordinates.typeName -> {
                                appendField(childField.name)
                            }
                            else -> {
                                appendInlineFragment(tn, childField.name)
                            }
                        }
                    }
                    else -> {
                        throw ServiceError.of(
                            "unsupported selection type [ type: %s ]",
                            ps::class.simpleName
                        )
                    }
                }
            }
    }

    private fun getGraphQLFieldDefinitionForField(
        materializationMetamodel: MaterializationMetamodel,
        parentCompositeType: GraphQLCompositeType,
        fieldName: String
    ): Try<GraphQLFieldDefinition> {
        return Try.attemptNullable {
                Introspection.getFieldDef(
                    materializationMetamodel.materializationGraphQLSchema,
                    parentCompositeType,
                    fieldName
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
                                """unable to get field_definition for field 
                                |[ parent_composite_type.name: %s, name: %s ]"""
                                    .flatten(),
                                parentCompositeType.name,
                                fieldName
                            )
                            .cause(t)
                            .build()
                    }
                }
            }
    }

    private fun traverseFieldItsArgumentsAndDescendents(
        materializationMetamodel: MaterializationMetamodel,
        queryComponentContextFactory: QueryComponentContextFactory,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>
    ): (QueryComponentContext) -> Sequence<Either<QueryComponentContext, QueryComponentContext>> {
        return { parentContext: QueryComponentContext ->
            sequenceOf(parentContext.right())
                .plus(
                    parentContext
                        .toOption()
                        .filterIsInstance<FieldComponentContext>()
                        .map { fcc: FieldComponentContext ->
                            createChildArgumentContextsForParentFieldContext(
                                materializationMetamodel,
                                queryComponentContextFactory,
                                fcc
                            )
                        }
                        .fold(::emptySequence, ::identity)
                        .map(ArgumentComponentContext::right)
                )
                .plus(
                    parentContext
                        .toOption()
                        .filterIsInstance<FieldComponentContext>()
                        .filter { sfcc: FieldComponentContext ->
                            sfcc.field.selectionSet
                                .toOption()
                                .mapNotNull(SelectionSet::getSelections)
                                .filter(List<Selection<*>>::isNotEmpty)
                                .isDefined()
                        }
                        .map { fcc: FieldComponentContext ->
                            createChildFieldContextsForParentFieldContext(
                                materializationMetamodel,
                                queryComponentContextFactory,
                                fragmentDefinitionsByName,
                                fcc
                            )
                        }
                        .fold(::emptySequence, ::identity)
                        .map(FieldComponentContext::left)
                )
        }
    }

    private fun createChildArgumentContextsForParentFieldContext(
        materializationMetamodel: MaterializationMetamodel,
        queryComponentContextFactory: QueryComponentContextFactory,
        parentFieldContext: FieldComponentContext,
    ): Sequence<ArgumentComponentContext> {
        return Try.attemptNullable({
                materializationMetamodel.materializationGraphQLSchema.getFieldDefinition(
                    parentFieldContext.fieldCoordinates
                )
            }) {
                ServiceError.of(
                    "graphql_field_definition not found for coordinates [ %s ] for parent field",
                    parentFieldContext.fieldCoordinates
                )
            }
            .map { gfd: GraphQLFieldDefinition ->
                val remainingArgumentsByName: MutableMap<String, GraphQLArgument> =
                    gfd.arguments.fold(mutableMapOf<String, GraphQLArgument>()) { mm, ga ->
                        mm.apply { put(ga.name, ga) }
                    }
                parentFieldContext.field.arguments
                    .toOption()
                    .getOrElse(::emptyList)
                    .fold(mutableListOf<Argument>()) {
                        directArgs: MutableList<Argument>,
                        a: Argument ->
                        remainingArgumentsByName.remove(a.name)
                        directArgs.apply { add(a) }
                    }
                    .let { directArgs: List<Argument> ->
                        directArgs
                            .asSequence()
                            .plus(
                                remainingArgumentsByName.asSequence().map {
                                    (_: String, ga: GraphQLArgument) ->
                                    Argument.newArgument()
                                        .name(ga.name)
                                        .value(
                                            getDefaultValueForGraphQLArgumentIfPossible(ga)
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        """default_value for argument 
                                                        |[ field_coordinates: %s, argument.name: %s ] 
                                                        |could not be set [ type: %s ]
                                                        """
                                                            .flatten(),
                                                        parentFieldContext.fieldCoordinates,
                                                        ga.name,
                                                        Value::class.qualifiedName
                                                    )
                                                }
                                                .orElseThrow()
                                        )
                                        .build()
                                }
                            )
                    }
                    .map { a: Argument ->
                        queryComponentContextFactory
                            .argumentComponentContextBuilder()
                            .argument(a)
                            .fieldCoordinates(parentFieldContext.fieldCoordinates)
                            .path(parentFieldContext.path.transform { argument(a.name) })
                            .canonicalPath(
                                parentFieldContext.canonicalPath.transform { argument(a.name) }
                            )
                            .build()
                    }
            }
            .orElseThrow()
    }

    private fun getDefaultValueForGraphQLArgumentIfPossible(
        graphQLArgument: GraphQLArgument
    ): Option<Value<*>> {
        // case 1: arg default value is set on GraphQLArgument
        return graphQLArgument.argumentDefaultValue
            .toOption()
            .filter(InputValueWithState::isSet)
            .filter(InputValueWithState::isLiteral)
            .mapNotNull(InputValueWithState::getValue)
            .filterIsInstance<Value<*>>()
            .orElse {
                // case 2: arg type is nullable
                graphQLArgument.type
                    .toOption()
                    .filter(GraphQLTypeUtil::isNullable)
                    .filterNot(GraphQLTypeUtil::isList)
                    .and(NullValue.of().some())
            }
            .orElse {
                // case 3: arg type is list
                graphQLArgument.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapNonNull)
                    .filter(GraphQLTypeUtil::isList)
                    .and(ArrayValue.newArrayValue().build().some())
            }
    }

    private fun createChildFieldContextsForParentFieldContext(
        materializationMetamodel: MaterializationMetamodel,
        queryComponentContextFactory: QueryComponentContextFactory,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
        parentFieldContext: FieldComponentContext,
    ): Sequence<FieldComponentContext> {
        val parentFieldsContainerType: GraphQLFieldsContainer =
            Try.attemptNullable({
                    materializationMetamodel.materializationGraphQLSchema.getFieldDefinition(
                        parentFieldContext.fieldCoordinates
                    )
                }) {
                    ServiceError.of(
                        "graphql_field_definition not found at [ coordinates: %s ]",
                        parentFieldContext.fieldCoordinates
                    )
                }
                .map(GraphQLFieldDefinition::getType)
                .map(GraphQLTypeUtil::unwrapAll)
                .filterInstanceOf<GraphQLFieldsContainer>()
                .orElseThrow()
        return parentFieldContext.field.selectionSet
            .toOption()
            .mapNotNull(SelectionSet::getSelections)
            .map(List<Selection<*>>::asSequence)
            .getOrElse(::emptySequence)
            .map { s: Selection<*> -> none<Selection<*>>() to s }
            .recurseDepthFirst { (parentSelection: Option<Selection<*>>, s: Selection<*>) ->
                when (s) {
                    is Field -> {
                        sequenceOf(
                            createSelectedFieldComponentContextForChildSelection(
                                    materializationMetamodel,
                                    fragmentDefinitionsByName,
                                    queryComponentContextFactory,
                                    parentFieldsContainerType,
                                    parentFieldContext,
                                    parentSelection,
                                    s
                                )
                                .right()
                        )
                    }
                    is InlineFragment -> {
                        s.selectionSet
                            .toOption()
                            .mapNotNull(SelectionSet::getSelections)
                            .fold(::emptySequence, List<Selection<*>>::asSequence)
                            .map { cs: Selection<*> -> (s.some() to cs).left() }
                    }
                    is FragmentSpread -> {
                        fragmentDefinitionsByName
                            .getOrNone(s.name)
                            .mapNotNull(FragmentDefinition::getSelectionSet)
                            .mapNotNull(SelectionSet::getSelections)
                            .fold(::emptySequence, List<Selection<*>>::asSequence)
                            .map { cs: Selection<*> -> (s.some() to cs).left() }
                    }
                    else -> {
                        emptySequence()
                    }
                }
            }
    }

    private fun createSelectedFieldComponentContextForChildSelection(
        materializationMetamodel: MaterializationMetamodel,
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
        queryComponentContextFactory: QueryComponentContextFactory,
        parentFieldsContainerType: GraphQLFieldsContainer,
        parentFieldContext: FieldComponentContext,
        parentSelection: Option<Selection<*>>,
        childField: Field,
    ): FieldComponentContext {
        val gfc: GraphQLFieldsContainer =
            when {
                parentFieldsContainerType.getFieldDefinition(childField.name) != null -> {
                    parentFieldsContainerType
                }
                else -> {
                    parentFieldsContainerType
                        .toOption()
                        .filterIsInstance<GraphQLInterfaceType>()
                        .mapNotNull { git: GraphQLInterfaceType ->
                            materializationMetamodel.materializationGraphQLSchema
                                .getImplementations(git)
                        }
                        .fold(::emptySequence, List<GraphQLObjectType>::asSequence)
                        .firstOrNone { got: GraphQLObjectType ->
                            got.getFieldDefinition(childField.name) != null
                        }
                        .successIfDefined {
                            ServiceError.of(
                                """could not find field by name 
                                |[ name: %s ] under parent_graphql_fields_container 
                                |interface or implementation type [ type_name: %s ]"""
                                    .flatten(),
                                childField.name,
                                parentFieldsContainerType.name
                            )
                        }
                        .orElseThrow()
                }
            }
        val gqlfd: GraphQLFieldDefinition =
            getGraphQLFieldDefinitionForField(materializationMetamodel, gfc, childField.name)
                .orElseThrow()
        val fc: FieldCoordinates = FieldCoordinates.coordinates(gfc, gqlfd)
        val p: GQLOperationPath =
            calculatePathForChildFieldOnParentSelection(
                parentFieldContext.path.some(),
                parentSelection,
                childField,
                fc,
                fragmentDefinitionsByName
            )
        val cp: GQLOperationPath =
            calculateCanonicalPathForChildFieldOnParentSelection(
                parentFieldContext.canonicalPath.some(),
                parentSelection,
                childField,
                fc,
                fragmentDefinitionsByName
            )
        return queryComponentContextFactory
            .fieldComponentContextBuilder()
            .field(childField)
            .fieldCoordinates(fc)
            .path(p)
            .canonicalPath(cp)
            .build()
    }

    private fun featuresLastComparator(
        fragmentDefinitionsByName: Map<String, FragmentDefinition>,
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
        val fieldResolver: (Selection<*>) -> Sequence<Either<Selection<*>, Field>> =
            { s: Selection<*> ->
                when (s) {
                    is Field -> {
                        sequenceOf(s.right())
                    }
                    is InlineFragment -> {
                        s.selectionSet
                            .toOption()
                            .mapNotNull(SelectionSet::getSelections)
                            .fold(::emptySequence, List<Selection<*>>::asSequence)
                            .map(Selection<*>::left)
                    }
                    is FragmentSpread -> {
                        fragmentDefinitionsByName
                            .getOrNone(s.name)
                            .mapNotNull(FragmentDefinition::getSelectionSet)
                            .mapNotNull(SelectionSet::getSelections)
                            .fold(::emptySequence, List<Selection<*>>::asSequence)
                            .map(Selection<*>::left)
                    }
                    else -> {
                        emptySequence()
                    }
                }
            }
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
                        .recurseDepthFirst(fieldResolver)
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
                        .recurseDepthFirst(fieldResolver)
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
