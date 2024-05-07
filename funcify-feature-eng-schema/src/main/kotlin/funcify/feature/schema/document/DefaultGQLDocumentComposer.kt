package funcify.feature.schema.document

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.document.DefaultGQLDocumentComposer.DocumentTraverser
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectedField
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.schema.sdl.type.GraphQLExactSDLTypeComposer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PairExtensions.bimap
import funcify.feature.tools.extensions.PairExtensions.fold
import funcify.feature.tools.extensions.PairExtensions.mapFirst
import funcify.feature.tools.extensions.PairExtensions.mapSecond
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TripleExtensions.fold
import funcify.feature.tools.extensions.TripleExtensions.mapFirst
import funcify.feature.tools.extensions.TripleExtensions.mapSecond
import funcify.feature.tools.extensions.TripleExtensions.mapThird
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.Node
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.TypeName
import graphql.language.Value
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @author smccarron
 * @created 2023-10-12
 */
internal object DefaultGQLDocumentComposer : GQLDocumentComposer {

    private val logger: Logger = loggerFor<DefaultGQLDocumentComposer>()

    /**
     * [GraphQLSchema]s as of v20 do not have hashcode implementations, so their identity hash code
     * must be used instead
     */
    private val cache: ConcurrentMap<Pair<Int, GQLDocumentSpec>, Try<Document>> =
        ConcurrentHashMap()

    // TODO: Incorporate directive specs
    // TODO: Add support for handling non-scalar argument values
    // TODO: Check handling for empty specs
    // override fun composeDocumentFromSpecWithMetamodel(
    //    spec: GQLDocumentSpec,
    //    materializationMetamodel: MaterializationMetamodel
    // ): Try<Document> {
    //    logger.info(
    //        "compose_document_from_spec_with_metamodel: [ spec.field_paths.size: {} ]",
    //        spec.fieldPaths.size
    //    )
    //    return cache.computeIfAbsent(
    //        materializationMetamodel.created to spec,
    //        calculateDocumentForSpecWithSchema(
    //            materializationMetamodel.materializationGraphQLSchema
    //        )
    //    )
    // }

    override fun composeDocumentFromSpecWithSchema(
        spec: GQLDocumentSpec,
        graphQLSchema: GraphQLSchema
    ): Try<Document> {
        logger.info(
            "compose_document_from_spec_with_schema: [ spec.field_paths.size: {} ]",
            spec.fieldPaths.size
        )
        return cache.computeIfAbsent(
            System.identityHashCode(graphQLSchema) to spec,
            calculateDocumentForSpecWithSchema(graphQLSchema)
        )
    }

    private fun calculateDocumentForSpecWithSchema(
        graphQLSchema: GraphQLSchema
    ): (Pair<Int, GQLDocumentSpec>) -> Try<Document> {
        return { (_: Int, spec: GQLDocumentSpec) ->
            spec.fieldPaths
                .asSequence()
                .fold(persistentMapOf<Int, PersistentSet<GQLOperationPath>>()) {
                    pm: PersistentMap<Int, PersistentSet<GQLOperationPath>>,
                    p: GQLOperationPath ->
                    when {
                        p.isRoot() -> {
                            pm
                        }
                        else -> {
                            updateFieldPathsByLevelMapWithPath(pm, p)
                        }
                    }
                }
                .let { pm: PersistentMap<Int, PersistentSet<GQLOperationPath>> ->
                    createDocumentFromCreationContext(
                        DocumentCreationContext(
                            spec = spec,
                            schema = graphQLSchema,
                            fieldPathsByLevel = pm
                        )
                    )
                }
        }
    }

    private fun updateChildFieldPathsByParentFieldPathMapWithPath(
        childPathsByParentPathMap: PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
        nextPath: GQLOperationPath,
    ): PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> {
        return (childPathsByParentPathMap to nextPath)
            .toOption()
            .filter { (pm, p) -> p !in pm }
            .map { (pm, p) -> pm.put(p, pm.getOrElse(p, ::persistentSetOf)) to p }
            .recurse { (pm, p) ->
                when (val pp: GQLOperationPath? = p.getParentPath().orNull()) {
                    null -> {
                        pm.right().some()
                    }
                    in pm -> {
                        pm.put(pp, pm.getOrElse(pp, ::persistentSetOf).add(p)).right().some()
                    }
                    else -> {
                        (pm.put(pp, pm.getOrElse(pp, ::persistentSetOf).add(p)) to pp).left().some()
                    }
                }
            }
            .getOrElse { childPathsByParentPathMap }
    }

    private fun updateFieldPathsByLevelMapWithPath(
        fieldPathsByLevel: PersistentMap<Int, PersistentSet<GQLOperationPath>>,
        nextPath: GQLOperationPath,
    ): PersistentMap<Int, PersistentSet<GQLOperationPath>> {
        return (fieldPathsByLevel to nextPath)
            .toOption()
            .filterNot { (pm, p) -> pm.getOrElse(p.level(), ::persistentSetOf).contains(p) }
            .map { (pm, p) ->
                pm.put(p.level(), pm.getOrElse(p.level(), ::persistentSetOf).add(p)) to p
            }
            .recurse { (pm, p) ->
                when (
                    val pp: GQLOperationPath? =
                        p.getParentPath()
                            .filterNot { pp: GQLOperationPath ->
                                pm.getOrElse(pp.level(), ::persistentSetOf).contains(pp)
                            }
                            .orNull()
                ) {
                    null -> {
                        pm.right().some()
                    }
                    else -> {
                        (pm.put(pp.level(), pm.getOrElse(pp.level(), ::persistentSetOf).add(pp)) to
                                pp)
                            .left()
                            .some()
                    }
                }
            }
            .getOrElse { fieldPathsByLevel }
    }

    private fun createDocumentFromCreationContext(
        documentCreationContext: DocumentCreationContext
    ): Try<Document> {
        /*if (logger.isDebugEnabled) {
            logger.debug(
                "create_document_from_creation_context: [ field_paths_by_level: {} ]",
                documentCreationContext.fieldPathsByLevel
                    .asSequence()
                    .sortedBy { (l: Int, _: Set<GQLOperationPath>) -> l }
                    .joinToString { (l: Int, ps: Set<GQLOperationPath>) ->
                        "[${l}]: [ ${ps.asSequence().joinToString()} ]"
                    }
            )
        }*/
        return documentCreationContext.fieldPathsByLevel.keys
            .asSequence()
            .filter { level: Int -> level != 0 }
            .sorted()
            .fold(
                DocumentTraversalContext(
                    parentImplementingTypeByParentPath =
                        persistentMapOf(
                            GQLOperationPath.getRootPath() to
                                documentCreationContext.schema.queryType
                        ),
                    documentTraverser = ::identity
                )
            ) { dtc: DocumentTraversalContext, level: Int ->
                /*if (logger.isDebugEnabled) {
                    logger.debug(
                        "document_traversal_context: [ level: {}, parent_implementing_type_by_parent_path.keys: {} ]",
                        level,
                        dtc.parentImplementingTypeByParentPath.asSequence().joinToString {
                            (p: GQLOperationPath, git: GraphQLImplementingType) ->
                            "$p: ${git.name}"
                        }
                    )
                }*/
                DocumentTraversalContext(
                    parentImplementingTypeByParentPath =
                        createParentImplementingTypeByParentPathForChildrenAtLevel(
                            documentCreationContext,
                            dtc,
                            level
                        ),
                    documentTraverser =
                        DocumentTraverser(
                            dtc.documentTraverser.compose(
                                createMapOfParentsForChildrenAtLevel(
                                    documentCreationContext,
                                    dtc,
                                    level
                                )
                            )
                        )
                )
            }
            .let { dtc: DocumentTraversalContext ->
                Try.attempt { dtc.documentTraverser(persistentMapOf()) }
                    .filter({ m: Map<GQLOperationPath, TraversedFieldContext> ->
                        m.containsKey(GQLOperationPath.getRootPath())
                    }) { _: Map<GQLOperationPath, TraversedFieldContext> ->
                        ServiceError.of("traversal failed to produce mapping for root")
                    }
                    .flatMap { m: Map<GQLOperationPath, TraversedFieldContext> ->
                        Try.fromOption(
                            m.getOrNone(GQLOperationPath.getRootPath()).map {
                                dtc: TraversedFieldContext ->
                                initialDocumentTraversalFunction().invoke(dtc)
                            }
                        )
                    }
            }
    }

    private fun createParentImplementingTypeByParentPathForChildrenAtLevel(
        documentCreationContext: DocumentCreationContext,
        documentTraversalContext: DocumentTraversalContext,
        level: Int,
    ): PersistentMap<GQLOperationPath, GraphQLImplementingType> {
        return documentCreationContext.fieldPathsByLevel
            .getOrNone(level)
            .fold(::emptySequence, Set<GQLOperationPath>::asSequence)
            .map { p: GQLOperationPath ->
                p.getParentPath().flatMap { pp: GQLOperationPath ->
                    documentTraversalContext.parentImplementingTypeByParentPath.getOrNone(pp).map {
                        git: GraphQLImplementingType ->
                        git to p
                    }
                }
            }
            .flatMapOptions()
            .map { (git: GraphQLImplementingType, p: GQLOperationPath) ->
                p.selection
                    .lastOrNone()
                    .map { ss: SelectionSegment ->
                        when (ss) {
                            is FieldSegment -> {
                                ss.fieldName
                            }
                            is AliasedFieldSegment -> {
                                ss.fieldName
                            }
                            is FragmentSpreadSegment -> {
                                ss.selectedField.fieldName
                            }
                            is InlineFragmentSegment -> {
                                ss.selectedField.fieldName
                            }
                        }
                    }
                    .flatMap { fn: String ->
                        git.getFieldDefinition(fn).toOption().orElse {
                            // TODO: The following determination of the implementation type to use
                            // as the parent container type for children of the next level will need
                            // to be revisited once subtyping strategies have been sorted out;
                            // Implementing these changes may require changing the API for the
                            // [GQLDocumentComposer]
                            git.toOption().filterIsInstance<GraphQLInterfaceType>().flatMap {
                                inf: GraphQLInterfaceType ->
                                documentCreationContext.schema
                                    .getImplementations(inf)
                                    .asSequence()
                                    .map { got: GraphQLObjectType ->
                                        got.getFieldDefinition(fn).toOption()
                                    }
                                    .flatMapOptions()
                                    .firstOrNone()
                            }
                        }
                    }
                    .map { gfd: GraphQLFieldDefinition -> GraphQLTypeUtil.unwrapAll(gfd.type) }
                    .filterIsInstance<GraphQLImplementingType>()
                    .map { git1: GraphQLImplementingType -> p to git1 }
            }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    private fun createMapOfParentsForChildrenAtLevel(
        documentCreationContext: DocumentCreationContext,
        parentLevelDocumentTraversalContext: DocumentTraversalContext,
        level: Int,
    ): DocumentTraverser {
        return DocumentTraverser {
            childContextsByPath: ImmutableMap<GQLOperationPath, TraversedFieldContext> ->
            /*if (logger.isDebugEnabled) {
                logger.debug(
                    "create_map_of_parents_for_children_at_level: [ level: {}, child_contexts_by_path.keys: {} ]",
                    level,
                    childContextsByPath.keys.asSequence().joinToString()
                )
            }*/
            documentCreationContext.fieldPathsByLevel
                .getOrNone(level)
                .fold(::emptySequence, Set<GQLOperationPath>::asSequence)
                .map { p: GQLOperationPath ->
                    childContextsByPath.getOrNone(p).getOrElse {
                        DefaultTraversedFieldContext(path = p)
                    }
                }
                .map { tfc: TraversedFieldContext ->
                    tfc.path.getParentPath().flatMap { pp: GQLOperationPath ->
                        parentLevelDocumentTraversalContext.parentImplementingTypeByParentPath
                            .getOrNone(pp)
                            .map { git: GraphQLImplementingType ->
                                extractFieldDefinitionForChildNodeFromParentImplementingType(
                                    documentCreationContext,
                                    git,
                                    tfc
                                )
                            }
                            .map { gfd: GraphQLFieldDefinition ->
                                pp to
                                    DefaultTraversedFieldContextWithFieldDef(
                                        existingContext = tfc,
                                        graphQLFieldDefinition = gfd
                                    )
                            }
                    }
                }
                .flatMapOptions()
                .fold(
                    persistentMapOf<
                        GQLOperationPath,
                        Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>
                    >()
                ) { pm, (pp, cf) ->
                    updateMapWithSelectionGroupsForParentPerChildTraversedFieldContext(pm, pp, cf)
                }
                .asSequence()
                .map {
                    (
                        pp: GQLOperationPath,
                        csm: Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>) ->
                    convertChildSelectionGroupsIntoParentTraversedFieldContext(
                        documentCreationContext,
                        pp,
                        csm
                    )
                }
                .fold(persistentMapOf()) {
                    pm: PersistentMap<GQLOperationPath, TraversedFieldContext>,
                    traversedFieldContext: TraversedFieldContext ->
                    pm.put(traversedFieldContext.path, traversedFieldContext)
                }
        }
    }

    private fun extractFieldDefinitionForChildNodeFromParentImplementingType(
        documentCreationContext: DocumentCreationContext,
        graphQLImplementingType: GraphQLImplementingType,
        traversedFieldContext: TraversedFieldContext,
    ): GraphQLFieldDefinition {
        return traversedFieldContext.path.selection
            .lastOrNone()
            .map { ss: SelectionSegment ->
                when (ss) {
                    is FieldSegment -> {
                        ss.fieldName
                    }
                    is AliasedFieldSegment -> {
                        ss.fieldName
                    }
                    is FragmentSpreadSegment -> {
                        ss.selectedField.fieldName
                    }
                    is InlineFragmentSegment -> {
                        ss.selectedField.fieldName
                    }
                }
            }
            .flatMap { fn: String ->
                graphQLImplementingType.getFieldDefinition(fn).toOption().orElse {
                    graphQLImplementingType
                        .toOption()
                        .filterIsInstance<GraphQLInterfaceType>()
                        .flatMap { git: GraphQLInterfaceType ->
                            documentCreationContext.schema
                                .getImplementations(git)
                                .asSequence()
                                .mapNotNull { got: GraphQLObjectType -> got.getFieldDefinition(fn) }
                                .firstOrNone()
                        }
                }
            }
            .successIfDefined {
                ServiceError.of(
                    """unable to find graphql_field_definition for path [ %s ] 
                    |in parent implementing type [ name: %s ]"""
                        .flatten(),
                    traversedFieldContext.path,
                    graphQLImplementingType.name
                )
            }
            .orElseThrow()
    }

    private fun updateMapWithSelectionGroupsForParentPerChildTraversedFieldContext(
        map:
            PersistentMap<
                GQLOperationPath,
                Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>
            >,
        parentPath: GQLOperationPath,
        childContext: DefaultTraversedFieldContextWithFieldDef
    ): PersistentMap<
        GQLOperationPath,
        Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>
    > {
        return when (
            val ss: SelectionSegment? = childContext.path.selection.lastOrNone().orNull()
        ) {
            null -> {
                throw ServiceError.of(
                    "child path must have at least one selection_segment [ %s ]",
                    childContext
                )
            }
            is AliasedFieldSegment -> {
                SelectionGroup()
            }
            is FieldSegment -> {
                SelectionGroup()
            }
            is FragmentSpreadSegment -> {
                SelectionGroup(fragmentName = ss.fragmentName, typeName = ss.typeName)
            }
            is InlineFragmentSegment -> {
                SelectionGroup(fragmentName = "", typeName = ss.typeName)
            }
        }.let { sg: SelectionGroup ->
            map.put(
                parentPath,
                map.getOrElse(parentPath, ::persistentMapOf)
                    .toPersistentMap()
                    .put(
                        sg,
                        map.getOrElse(parentPath, ::persistentMapOf)
                            .getOrElse(sg, ::persistentListOf)
                            .toPersistentList()
                            .add(childContext)
                    )
            )
        }
    }

    private fun convertChildSelectionGroupsIntoParentTraversedFieldContext(
        documentCreationContext: DocumentCreationContext,
        parentPath: GQLOperationPath,
        childSelectionGroups: Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>,
    ): TraversedFieldContext {
        /*if (logger.isDebugEnabled) {
            logger.debug(
                "convert_child_selection_groups_into_parent_traversed_field_context: [ parent_path: {} ]",
                parentPath
            )
        }*/
        return childSelectionGroups
            .asSequence()
            .flatMap { (sg: SelectionGroup, tfcs: List<DefaultTraversedFieldContextWithFieldDef>) ->
                when {
                    sg.fragmentName.isBlank() && sg.typeName.isBlank() -> {
                        tfcs.asSequence().flatMap { tfc: DefaultTraversedFieldContextWithFieldDef ->
                            createFieldAndOtherNodesFromTraversedFieldContextWithFieldDef(
                                documentCreationContext,
                                tfc
                            )
                        }
                    }
                    sg.fragmentName.isBlank() -> {
                        tfcs
                            .asSequence()
                            .flatMap { tfc: DefaultTraversedFieldContextWithFieldDef ->
                                createFieldAndOtherNodesFromTraversedFieldContextWithFieldDef(
                                    documentCreationContext,
                                    tfc
                                )
                            }
                            .fold(
                                persistentListOf<Selection<*>>() to persistentListOf<Node<*>>()
                            ) { selAndOther, n ->
                                when (n) {
                                    is Selection<*> -> {
                                        selAndOther.mapFirst { selections -> selections.add(n) }
                                    }
                                    else -> {
                                        selAndOther.mapSecond { otherNodes -> otherNodes.add(n) }
                                    }
                                }
                            }
                            .fold { selections, otherNodes ->
                                sequenceOf(
                                        InlineFragment.newInlineFragment()
                                            .typeCondition(TypeName(sg.typeName))
                                            .selectionSet(
                                                SelectionSet.newSelectionSet()
                                                    .selections(selections)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .plus(otherNodes)
                            }
                    }
                    else -> {
                        tfcs
                            .asSequence()
                            .flatMap { tfc: DefaultTraversedFieldContextWithFieldDef ->
                                createFieldAndOtherNodesFromTraversedFieldContextWithFieldDef(
                                    documentCreationContext,
                                    tfc
                                )
                            }
                            .fold(
                                persistentListOf<Selection<*>>() to persistentListOf<Node<*>>()
                            ) { selAndOther, n ->
                                when (n) {
                                    is Selection<*> -> {
                                        selAndOther.mapFirst { selections -> selections.add(n) }
                                    }
                                    else -> {
                                        selAndOther.mapSecond { otherNodes -> otherNodes.add(n) }
                                    }
                                }
                            }
                            .fold { selections, otherNodes ->
                                sequenceOf(
                                        FragmentSpread.newFragmentSpread()
                                            .name(sg.fragmentName)
                                            .build(),
                                        FragmentDefinition.newFragmentDefinition()
                                            .name(sg.fragmentName)
                                            .typeCondition(TypeName(sg.typeName))
                                            .selectionSet(
                                                SelectionSet.newSelectionSet()
                                                    .selections(selections)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .plus(otherNodes)
                            }
                    }
                }
            }
            .fold(
                Triple(
                    persistentListOf<Selection<*>>(),
                    persistentListOf<VariableDefinition>(),
                    persistentListOf<FragmentDefinition>()
                )
            ) { selVarFrag, n ->
                when (n) {
                    is Selection<*> -> {
                        selVarFrag.mapFirst { selections -> selections.add(n) }
                    }
                    is VariableDefinition -> {
                        selVarFrag.mapSecond { variableDefinitions -> variableDefinitions.add(n) }
                    }
                    is FragmentDefinition -> {
                        selVarFrag.mapThird { fragmentDefinitions -> fragmentDefinitions.add(n) }
                    }
                    else -> {
                        selVarFrag
                    }
                }
            }
            .fold {
                selections: PersistentList<Selection<*>>,
                variableDefinitions: PersistentList<VariableDefinition>,
                fragmentDefinitions: PersistentList<FragmentDefinition> ->
                DefaultTraversedFieldContext(
                    path = parentPath,
                    selectionSet = SelectionSet.newSelectionSet().selections(selections).build(),
                    variableDefinitionsByName =
                        variableDefinitions
                            .asSequence()
                            .map { vd: VariableDefinition -> vd.name to vd }
                            .reducePairsToPersistentMap(),
                    fragmentDefinitions = fragmentDefinitions
                )
            }
    }

    private fun createFieldAndOtherNodesFromTraversedFieldContextWithFieldDef(
        documentCreationContext: DocumentCreationContext,
        traversedFieldContextWithFieldDef: DefaultTraversedFieldContextWithFieldDef,
    ): Sequence<Node<*>> {
        /*if (logger.isDebugEnabled) {
            logger.debug(
                "create_field_and_other_nodes_from_traversed_field_context_with_field_def: [ path: {} ]",
                traversedFieldContextWithFieldDef.path
            )
        }*/
        return traversedFieldContextWithFieldDef.graphQLFieldDefinition.arguments
            .asSequence()
            .map { ga: GraphQLArgument ->
                createArgumentAndConditionallyVariableDefinition(
                    documentCreationContext,
                    traversedFieldContextWithFieldDef,
                    ga
                )
            }
            .fold(persistentListOf<Argument>() to persistentListOf<VariableDefinition>()) {
                argsAndVars,
                avPair ->
                argsAndVars.bimap(
                    { args: PersistentList<Argument> -> args.add(avPair.first) },
                    { vars: PersistentList<VariableDefinition> ->
                        avPair.second
                            .map { vd: VariableDefinition -> vars.add(vd) }
                            .getOrElse { vars }
                    }
                )
            }
            .fold { arguments: List<Argument>, variableDefinitions: List<VariableDefinition> ->
                when (
                    val sf: SelectedField? =
                        traversedFieldContextWithFieldDef.path.selection
                            .lastOrNone()
                            .map { ss: SelectionSegment ->
                                when (ss) {
                                    is FieldSegment -> {
                                        ss
                                    }
                                    is AliasedFieldSegment -> {
                                        ss
                                    }
                                    is FragmentSpreadSegment -> {
                                        ss.selectedField
                                    }
                                    is InlineFragmentSegment -> {
                                        ss.selectedField
                                    }
                                }
                            }
                            .orNull()
                ) {
                    null -> {
                        throw ServiceError.of("path must have at least one level")
                    }
                    is FieldSegment -> {
                        sequenceOf(
                            Field.newField()
                                .name(sf.fieldName)
                                .arguments(arguments)
                                .selectionSet(
                                    useTraversedFieldContextSelectionSetIfNonEmpty(
                                        traversedFieldContextWithFieldDef
                                    )
                                )
                                .build()
                        )
                    }
                    is AliasedFieldSegment -> {
                        sequenceOf(
                            Field.newField()
                                .alias(sf.alias)
                                .name(sf.fieldName)
                                .arguments(arguments)
                                .selectionSet(
                                    useTraversedFieldContextSelectionSetIfNonEmpty(
                                        traversedFieldContextWithFieldDef
                                    )
                                )
                                .build()
                        )
                    }
                }.plus(
                    addNewVariableDefinitionsIfNoneRedefineExisting(
                        traversedFieldContextWithFieldDef,
                        variableDefinitions
                    )
                )
            }
            .plus(traversedFieldContextWithFieldDef.variableDefinitionsByName.values)
            .plus(traversedFieldContextWithFieldDef.fragmentDefinitions)
    }

    private fun createArgumentAndConditionallyVariableDefinition(
        documentCreationContext: DocumentCreationContext,
        traversedFieldContextWithFieldDef: DefaultTraversedFieldContextWithFieldDef,
        graphQLArgument: GraphQLArgument,
    ): Pair<Argument, Option<VariableDefinition>> {
        val argumentPath: GQLOperationPath =
            traversedFieldContextWithFieldDef.path.transform { argument(graphQLArgument.name) }
        return when {
            argumentPath in documentCreationContext.variableNameByArgumentPath -> {
                val variableNameToAssign: String =
                    documentCreationContext.variableNameByArgumentPath[argumentPath]!!
                Argument.newArgument()
                    .name(graphQLArgument.name)
                    .value(
                        VariableReference.newVariableReference().name(variableNameToAssign).build()
                    )
                    .build() to
                    VariableDefinition.newVariableDefinition()
                        .name(variableNameToAssign)
                        .type(GraphQLExactSDLTypeComposer.invoke(graphQLArgument.type))
                        .defaultValue(extractGraphQLArgumentDefaultLiteralValue(graphQLArgument))
                        .build()
                        .some()
            }
            argumentPath in documentCreationContext.argumentDefaultLiteralValuesByPath -> {
                Argument.newArgument()
                    .name(graphQLArgument.name)
                    .value(documentCreationContext.argumentDefaultLiteralValuesByPath[argumentPath])
                    .build() to none()
            }
            graphQLArgument.hasSetDefaultValue() -> {
                Argument.newArgument()
                    .name(graphQLArgument.name)
                    .value(extractGraphQLArgumentDefaultLiteralValue(graphQLArgument))
                    .build() to none()
            }
            argumentHasListType(graphQLArgument) -> {
                Argument.newArgument()
                    .name(graphQLArgument.name)
                    .value(ArrayValue.newArrayValue().build())
                    .build() to none()
            }
            GraphQLTypeUtil.isNullable(graphQLArgument.type) -> {
                Argument.newArgument().name(graphQLArgument.name).value(NullValue.of()).build() to
                    none()
            }
            else -> {
                throw ServiceError.of(
                    """argument [ name: %s ] for field [ name: %s ] 
                    |does not have an assigned variable_name, 
                    |an assigned default GraphQL value, 
                    |nor a schema-defined default GraphQL value 
                    |for non-nullable input_type [ %s ]"""
                        .flatten(),
                    graphQLArgument.name,
                    traversedFieldContextWithFieldDef.graphQLFieldDefinition.name,
                    GraphQLTypeUtil.simplePrint(graphQLArgument.type)
                )
            }
        }
    }

    private fun argumentHasListType(graphQLArgument: GraphQLArgument): Boolean {
        return graphQLArgument.type
            .toOption()
            .recurse { gt: GraphQLType ->
                when (gt) {
                    is GraphQLNonNull -> {
                        gt.wrappedType.left().some()
                    }
                    is GraphQLList -> {
                        gt.right().some()
                    }
                    else -> {
                        none()
                    }
                }
            }
            .isDefined()
    }

    private fun extractGraphQLArgumentDefaultLiteralValue(
        graphQLArgument: GraphQLArgument
    ): Value<*>? {
        return graphQLArgument.argumentDefaultValue
            .toOption()
            .filter(InputValueWithState::isSet)
            .filter(InputValueWithState::isLiteral)
            .mapNotNull(InputValueWithState::getValue)
            .filterIsInstance<Value<*>>()
            .orNull()
    }

    private fun useTraversedFieldContextSelectionSetIfNonEmpty(
        traversedFieldContext: TraversedFieldContext
    ): SelectionSet? {
        return when {
            traversedFieldContext.selectionSet
                .toOption()
                .mapNotNull(SelectionSet::getSelections)
                .filter(List<Selection<*>>::isNotEmpty)
                .isDefined() -> {
                traversedFieldContext.selectionSet
            }
            else -> {
                null
            }
        }
    }

    private fun addNewVariableDefinitionsIfNoneRedefineExisting(
        traversedFieldContextWithFieldDef: DefaultTraversedFieldContextWithFieldDef,
        newVariableDefinitions: List<VariableDefinition>,
    ): Sequence<VariableDefinition> {
        return newVariableDefinitions
            .partition { vd: VariableDefinition ->
                traversedFieldContextWithFieldDef.variableDefinitionsByName
                    .getOrNone(vd.name)
                    .map { vd1: VariableDefinition -> !vd1.type.isEqualTo(vd.type) }
                    .getOrElse { false }
            }
            .fold {
                redefiningNewDefs: List<VariableDefinition>,
                otherNewDefs: List<VariableDefinition> ->
                when {
                    redefiningNewDefs.isNotEmpty() -> {
                        throw ServiceError.of(
                            """at least one variable_definition already exists 
                            |with same name but with different type 
                            |[ definitions: %s ]"""
                                .flatten(),
                            redefiningNewDefs.asSequence().joinToString()
                        )
                    }
                    else -> {
                        otherNewDefs.asSequence()
                    }
                }
            }
    }

    private fun initialDocumentTraversalFunction(): (TraversedFieldContext) -> Document {
        return { dtc: TraversedFieldContext ->
            Document.newDocument()
                .definitions(
                    sequenceOf(
                            OperationDefinition.newOperationDefinition()
                                .operation(OperationDefinition.Operation.QUERY)
                                .selectionSet(dtc.selectionSet)
                                .variableDefinitions(dtc.variableDefinitionsByName.values.toList())
                                .build()
                        )
                        .plus(dtc.fragmentDefinitions)
                        .toList()
                )
                .build()
        }
    }

    private fun interface DocumentTraverser :
        (ImmutableMap<GQLOperationPath, TraversedFieldContext>) -> ImmutableMap<
                GQLOperationPath,
                TraversedFieldContext
            >

    private data class DocumentCreationContext(
        private val spec: GQLDocumentSpec,
        val schema: GraphQLSchema,
        val fieldPathsByLevel: PersistentMap<Int, PersistentSet<GQLOperationPath>>,
    ) : GQLDocumentSpec by spec

    private interface TraversedFieldContext {
        val path: GQLOperationPath
        val selectionSet: SelectionSet
        val variableDefinitionsByName: PersistentMap<String, VariableDefinition>
        val fragmentDefinitions: PersistentList<FragmentDefinition>
    }

    private data class DefaultTraversedFieldContext(
        override val path: GQLOperationPath,
        override val selectionSet: SelectionSet = SelectionSet.newSelectionSet().build(),
        override val variableDefinitionsByName: PersistentMap<String, VariableDefinition> =
            persistentMapOf(),
        override val fragmentDefinitions: PersistentList<FragmentDefinition> = persistentListOf()
    ) : TraversedFieldContext

    private data class DefaultTraversedFieldContextWithFieldDef(
        private val existingContext: TraversedFieldContext,
        val graphQLFieldDefinition: GraphQLFieldDefinition,
    ) : TraversedFieldContext by existingContext

    private data class DocumentTraversalContext(
        val parentImplementingTypeByParentPath:
            PersistentMap<GQLOperationPath, GraphQLImplementingType>,
        val documentTraverser: DocumentTraverser,
    )

    private data class SelectionGroup(val fragmentName: String = "", val typeName: String = "")
}
