package funcify.feature.materializer.gql

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
import funcify.feature.materializer.gql.GQLOperationPathsToDocumentTransformer.DocumentTraverser
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectedField
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.schema.sdl.GraphQLNullableSDLTypeComposer
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
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.Node
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
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

object GQLOperationPathsToDocumentTransformer :
    (GraphQLSchema, Set<GQLOperationPath>) -> Try<Document> {

    // private val cache: ConcurrentMap<Pair<Instant, ImmutableSet<GQLOperationPath>>,
    // Try<Document>> =
    //    ConcurrentHashMap()

    private val logger: Logger = loggerFor<GQLOperationPathsToDocumentTransformer>()

    private val cache:
        ConcurrentMap<Pair<GraphQLSchema, ImmutableSet<GQLOperationPath>>, Try<Document>> =
        ConcurrentHashMap()

    // override fun invoke(
    //    materializationMetamodel: MaterializationMetamodel,
    //    paths: Set<GQLOperationPath>
    // ): Try<Document> {
    //    return cache.computeIfAbsent(
    //        materializationMetamodel.created to paths.toPersistentSet(),
    //        calculateDocumentForPathsSetWithMetamodel(materializationMetamodel)
    //    )
    // }

    // TODO: Incorporate directive specs
    // TODO: Add support for handling non-scalar argument values
    override fun invoke(graphQLSchema: GraphQLSchema, paths: Set<GQLOperationPath>): Try<Document> {
        return cache.computeIfAbsent(
            graphQLSchema to paths.toPersistentSet(),
            calculateDocumentForPathsSetWithMetamodel()
        )
    }

    private fun calculateDocumentForPathsSetWithMetamodel():
        (Pair<GraphQLSchema, ImmutableSet<GQLOperationPath>>) -> Try<Document> {
        return { (graphQLSchema: GraphQLSchema, pathsSet: ImmutableSet<GQLOperationPath>) ->
            pathsSet
                .asSequence()
                .fold(
                    DocumentCreationContext(
                        schema = graphQLSchema,
                        childFieldPathsByParentPath = persistentMapOf(),
                        fieldPathsByLevel = persistentMapOf(),
                        argumentPaths = persistentSetOf(),
                        directivePaths = persistentSetOf()
                    )
                ) { c: DocumentCreationContext, p: GQLOperationPath ->
                    when {
                        p.isRoot() -> {
                            c
                        }
                        p.refersToPartOfDirective() -> {
                            c.copy(directivePaths = c.directivePaths.add(p))
                        }
                        p.refersToPartOfArgument() -> {
                            c.copy(argumentPaths = c.argumentPaths.add(p))
                        }
                        else -> {
                            c.copy(
                                // TODO: This currently isn't used and may be dropped if no use is
                                // found
                                childFieldPathsByParentPath =
                                    updateChildFieldPathsByParentFieldPathMapWithPath(
                                        c.childFieldPathsByParentPath,
                                        p
                                    ),
                                fieldPathsByLevel =
                                    updateFieldPathsByLevelMapWithPath(c.fieldPathsByLevel, p)
                            )
                        }
                    }
                }
                .let { c: DocumentCreationContext -> createDocumentFromCreationContext(c) }
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
            .filter { (pm, p) -> !pm.getOrElse(p.level(), ::persistentSetOf).contains(p) }
            .map { (pm, p) ->
                pm.put(p.level(), pm.getOrElse(p.level(), ::persistentSetOf).add(p)) to p
            }
            .recurse { (pm, p) ->
                when (val pp: GQLOperationPath? = p.getParentPath().orNull()) {
                    null -> {
                        pm.right().some()
                    }
                    else -> {
                        when {
                            pm.getOrElse(pp.level(), ::persistentSetOf).contains(pp) -> {
                                pm.right().some()
                            }
                            else -> {
                                (pm.put(
                                        pp.level(),
                                        pm.getOrElse(pp.level(), ::persistentSetOf).add(pp)
                                    ) to pp)
                                    .left()
                                    .some()
                            }
                        }
                    }
                }
            }
            .getOrElse { fieldPathsByLevel }
    }

    private fun createDocumentFromCreationContext(
        documentCreationContext: DocumentCreationContext
    ): Try<Document> {
        logger.debug(
            "create_document_from_creation_context: [ field_paths_by_level: {} ]",
            documentCreationContext.fieldPathsByLevel
                .asSequence()
                .sortedBy { (l, fps) -> l }
                .joinToString { (l, ps) -> "[${l}]: [ ${ps.asSequence().joinToString()} ]" }
        )
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
                logger.debug(
                    "document_traversal_context: [ level: {}, parent_implementing_type_by_parent_path.keys: {} ]",
                    level,
                    dtc.parentImplementingTypeByParentPath.asSequence().joinToString { (p, git) ->
                        "$p: ${git.name}"
                    }
                )
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
        return DocumentTraverser { m: ImmutableMap<GQLOperationPath, TraversedFieldContext> ->
            logger.debug(
                "create_map_of_parents_for_children_at_level: [ level: {}, m.keys: {} ]",
                level,
                m.keys
            )
            documentCreationContext.fieldPathsByLevel
                .getOrNone(level)
                .fold(::emptySequence, Set<GQLOperationPath>::asSequence)
                .map { p: GQLOperationPath ->
                    m.getOrNone(p).getOrElse { DefaultTraversedFieldContext(path = p) }
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
                    "unable to find graphql_field_definition for path [ %s ] in parent implementing type [ name: %s ]",
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
        GQLOperationPath, Map<SelectionGroup, List<DefaultTraversedFieldContextWithFieldDef>>
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
        return traversedFieldContextWithFieldDef.graphQLFieldDefinition.arguments
            .asSequence()
            .map { ga: GraphQLArgument ->
                traversedFieldContextWithFieldDef.path
                    .transform { argument(ga.name) }
                    .let { ap: GQLOperationPath ->
                        when {
                            ap in documentCreationContext.argumentPaths -> {
                                Argument.newArgument()
                                    .name(ga.name)
                                    .value(
                                        VariableReference.newVariableReference()
                                            .name(ga.name)
                                            .build()
                                    )
                                    .build() to
                                    VariableDefinition.newVariableDefinition()
                                        .name(ga.name)
                                        .type(GraphQLNullableSDLTypeComposer.invoke(ga.type))
                                        .defaultValue(extractGraphQLArgumentDefaultLiteralValue(ga))
                                        .build()
                                        .some()
                            }
                            !ga.hasSetDefaultValue() -> {
                                throw ServiceError.of(
                                    """argument [ name: %s ] for field [ name: %s ] 
                                    |has not been specified as a selected path 
                                    |for variable_definition creation 
                                    |but does not have a default value 
                                    |for input_type [ %s ]"""
                                        .flatten(),
                                    ga.name,
                                    traversedFieldContextWithFieldDef.graphQLFieldDefinition.name,
                                    GraphQLTypeUtil.simplePrint(ga.type)
                                )
                            }
                            else -> {
                                Argument.newArgument()
                                    .name(ga.name)
                                    .value(extractGraphQLArgumentDefaultLiteralValue(ga))
                                    .build() to none()
                            }
                        }
                    }
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

    private fun extractGraphQLArgumentDefaultLiteralValue(
        graphQLArgument: GraphQLArgument
    ): Value<*>? {
        return graphQLArgument.argumentDefaultValue
            .toOption()
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

    private fun interface DocumentTraverser :
        (ImmutableMap<GQLOperationPath, TraversedFieldContext>) -> ImmutableMap<
                GQLOperationPath, TraversedFieldContext
            >

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

    private data class DocumentCreationContext(
        val schema: GraphQLSchema,
        val childFieldPathsByParentPath:
            PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
        val fieldPathsByLevel: PersistentMap<Int, PersistentSet<GQLOperationPath>>,
        val argumentPaths: PersistentSet<GQLOperationPath>,
        val directivePaths: PersistentSet<GQLOperationPath>
    )

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
