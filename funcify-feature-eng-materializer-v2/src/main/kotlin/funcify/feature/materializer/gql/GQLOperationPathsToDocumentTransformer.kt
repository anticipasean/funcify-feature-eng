package funcify.feature.materializer.gql

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.left
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
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PairExtensions.fold
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
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
import graphql.language.VariableDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
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

object GQLOperationPathsToDocumentTransformer :
    (GraphQLSchema, Set<GQLOperationPath>) -> Try<Document> {

    // private val cache: ConcurrentMap<Pair<Instant, ImmutableSet<GQLOperationPath>>,
    // Try<Document>> =
    //    ConcurrentHashMap()

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
                        argumentPathsByParentFieldPath = persistentMapOf(),
                        directivePathsByParentPath = persistentMapOf()
                    )
                ) { c: DocumentCreationContext, p: GQLOperationPath ->
                    when {
                        p.isRoot() -> {
                            c
                        }
                        p.referentOnDirective() -> {
                            p.getParentPath()
                                .map { pp: GQLOperationPath ->
                                    c.copy(
                                        directivePathsByParentPath =
                                            c.directivePathsByParentPath.put(
                                                pp,
                                                c.directivePathsByParentPath
                                                    .getOrElse(pp, ::persistentSetOf)
                                                    .add(p)
                                            )
                                    )
                                }
                                .getOrElse { c }
                        }
                        p.referentOnArgument() -> {
                            p.getParentPath()
                                .map { pp: GQLOperationPath ->
                                    c.copy(
                                        argumentPathsByParentFieldPath =
                                            c.argumentPathsByParentFieldPath.put(
                                                pp,
                                                c.argumentPathsByParentFieldPath
                                                    .getOrElse(pp, ::persistentSetOf)
                                                    .add(p)
                                            )
                                    )
                                }
                                .getOrElse { c }
                        }
                        else -> {
                            c.copy(
                                childFieldPathsByParentPath =
                                    updateChildFieldPathsByParentFieldPathMapWithPath(
                                        c.childFieldPathsByParentPath,
                                        p
                                    ),
                                fieldPathsByLevel =
                                    c.fieldPathsByLevel.put(
                                        p.level(),
                                        c.fieldPathsByLevel
                                            .getOrElse(p.level(), ::persistentSetOf)
                                            .add(p)
                                    )
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

    private fun createDocumentFromCreationContext(context: DocumentCreationContext): Try<Document> {
        return context.fieldPathsByLevel.keys
            .sorted()
            .fold(
                DocumentTraversalContext(
                    parentImplementingTypeByParentPath =
                        persistentMapOf(GQLOperationPath.getRootPath() to context.schema.queryType),
                    documentTraverser = ::identity
                )
            ) { dtc: DocumentTraversalContext, level: Int ->
                dtc.copy(
                    parentImplementingTypeByParentPath =
                        context.fieldPathsByLevel
                            .getOrNone(level)
                            .fold(::emptySequence, Set<GQLOperationPath>::asSequence)
                            .map { p: GQLOperationPath ->
                                p.getParentPath().flatMap { pp: GQLOperationPath ->
                                    dtc.parentImplementingTypeByParentPath.getOrNone(pp).map {
                                        git: GraphQLImplementingType ->
                                        git to p
                                    }
                                }
                            }
                            .flatMapOptions()
                            .map { (git: GraphQLImplementingType, p: GQLOperationPath) ->
                                Option.fromNullable(
                                        when (
                                            val s: SelectionSegment? =
                                                p.selection.lastOrNone().orNull()
                                        ) {
                                            null -> {
                                                null
                                            }
                                            is FieldSegment -> {
                                                s.fieldName
                                            }
                                            is AliasedFieldSegment -> {
                                                s.fieldName
                                            }
                                            is FragmentSpreadSegment -> {
                                                s.selectedField.fieldName
                                            }
                                            is InlineFragmentSegment -> {
                                                s.selectedField.fieldName
                                            }
                                        }
                                    )
                                    .flatMap { fn: String ->
                                        git.getFieldDefinition(fn).toOption().orElse {
                                            git.toOption()
                                                .filterIsInstance<GraphQLInterfaceType>()
                                                .flatMap { inf: GraphQLInterfaceType ->
                                                    context.schema
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
                                    .map { gfd: GraphQLFieldDefinition ->
                                        GraphQLTypeUtil.unwrapAll(gfd.type)
                                    }
                                    .filterIsInstance<GraphQLImplementingType>()
                                    .map { git1: GraphQLImplementingType -> p to git1 }
                            }
                            .flatMapOptions()
                            .reducePairsToPersistentMap(),
                    documentTraverser =
                        DocumentTraverser(
                            dtc.documentTraverser.compose(
                                createMapOfParentsForChildNodesAtLevel(context, level)
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

    private fun createMapOfParentsForChildNodesAtLevel(
        context: DocumentCreationContext,
        level: Int,
    ): DocumentTraverser {
        return DocumentTraverser { m: Map<GQLOperationPath, TraversedFieldContext> ->
            context.fieldPathsByLevel
                .getOrNone(level)
                .fold(::emptySequence, Set<GQLOperationPath>::asSequence)
                .map { p: GQLOperationPath ->
                    p to m.getOrNone(p).getOrElse { TraversedFieldContext() }
                }
                .map { (p: GQLOperationPath, tfc: TraversedFieldContext) ->
                    when (
                        val sf: SelectedField? =
                            p.selection
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
                            Field.newField()
                                .name(sf.fieldName)
                                .selectionSet(tfc.selectionSet)
                                .build()
                        }
                        is AliasedFieldSegment -> {
                            Field.newField()
                                .alias(sf.alias)
                                .name(sf.fieldName)
                                .selectionSet(tfc.selectionSet)
                                .build()
                        }
                    }.let { f: Field -> p to f }
                }
                .map { p: Pair<GQLOperationPath, Field> ->
                    p.first.getParentPath().map { pp: GQLOperationPath -> pp to p }
                }
                .flatMapOptions()
                .fold(
                    persistentMapOf<
                        GQLOperationPath,
                        Map<Pair<String, String>, List<Pair<GQLOperationPath, Field>>>
                    >()
                ) { pm, (pp, cf) ->
                    updateMapWithSelectionGroupsForParentPerChildField(pm, pp, cf.first, cf.second)
                }
                .asSequence()
                .map {
                    (
                        pp: GQLOperationPath,
                        csm: Map<Pair<String, String>, List<Pair<GQLOperationPath, Field>>>) ->
                    convertChildSelectionGroupsForParentIntoTraversedFieldContext(pp, csm)
                }
                .reducePairsToPersistentMap()
        }
    }

    private fun updateMapWithSelectionGroupsForParentPerChildField(
        map:
            PersistentMap<
                GQLOperationPath, Map<Pair<String, String>, List<Pair<GQLOperationPath, Field>>>
            >,
        parentPath: GQLOperationPath,
        childPath: GQLOperationPath,
        childField: Field,
    ): PersistentMap<
        GQLOperationPath, Map<Pair<String, String>, List<Pair<GQLOperationPath, Field>>>
    > {
        return when (val ss: SelectionSegment? = childPath.selection.lastOrNone().orNull()) {
            null -> {
                throw ServiceError.of(
                    "child path must have at least one selection_segment [ %s ]",
                    childPath
                )
            }
            is AliasedFieldSegment -> {
                val childGroupKey: Pair<String, String> = "" to ""
                map.put(
                    parentPath,
                    map.getOrElse(parentPath, ::persistentMapOf)
                        .toPersistentMap()
                        .put(
                            childGroupKey,
                            map.getOrElse(parentPath, ::persistentMapOf)
                                .getOrElse(childGroupKey, ::persistentListOf)
                                .toPersistentList()
                                .add(childPath to childField)
                        )
                )
            }
            is FieldSegment -> {
                val childGroupKey: Pair<String, String> = "" to ""
                map.put(
                    parentPath,
                    map.getOrElse(parentPath, ::persistentMapOf)
                        .toPersistentMap()
                        .put(
                            childGroupKey,
                            map.getOrElse(parentPath, ::persistentMapOf)
                                .getOrElse(childGroupKey, ::persistentListOf)
                                .toPersistentList()
                                .add(childPath to childField)
                        )
                )
            }
            is FragmentSpreadSegment -> {
                val childGroupKey: Pair<String, String> = ss.fragmentName to ss.typeName
                map.put(
                    parentPath,
                    map.getOrElse(parentPath, ::persistentMapOf)
                        .toPersistentMap()
                        .put(
                            childGroupKey,
                            map.getOrElse(parentPath, ::persistentMapOf)
                                .getOrElse(childGroupKey, ::persistentListOf)
                                .toPersistentList()
                                .add(childPath to childField)
                        )
                )
            }
            is InlineFragmentSegment -> {
                val childGroupKey: Pair<String, String> = "" to ss.typeName
                map.put(
                    parentPath,
                    map.getOrElse(parentPath, ::persistentMapOf)
                        .toPersistentMap()
                        .put(
                            childGroupKey,
                            map.getOrElse(parentPath, ::persistentMapOf)
                                .getOrElse(childGroupKey, ::persistentListOf)
                                .toPersistentList()
                                .add(childPath to childField)
                        )
                )
            }
        }
    }

    private fun convertChildSelectionGroupsForParentIntoTraversedFieldContext(
        parentPath: GQLOperationPath,
        childSelectionGroups: Map<Pair<String, String>, List<Pair<GQLOperationPath, Field>>>,
    ): Pair<GQLOperationPath, TraversedFieldContext> {
        return childSelectionGroups
            .asSequence()
            .flatMap {
                (
                    groupSignature: Pair<String, String>,
                    childFields: List<Pair<GQLOperationPath, Field>>) ->
                when {
                    groupSignature.first.isBlank() && groupSignature.second.isBlank() -> {
                        childFields.asSequence().map { (p: GQLOperationPath, f: Field) -> f }
                    }
                    groupSignature.first.isBlank() -> {
                        sequenceOf(
                            InlineFragment.newInlineFragment()
                                .typeCondition(TypeName(groupSignature.second))
                                .selectionSet(
                                    SelectionSet.newSelectionSet()
                                        .selections(
                                            childFields
                                                .asSequence()
                                                .map { (p: GQLOperationPath, f: Field) -> f }
                                                .toList()
                                        )
                                        .build()
                                )
                                .build()
                        )
                    }
                    else -> {
                        sequenceOf(
                            FragmentDefinition.newFragmentDefinition()
                                .name(groupSignature.first)
                                .typeCondition(TypeName(groupSignature.second))
                                .selectionSet(
                                    SelectionSet.newSelectionSet()
                                        .selections(
                                            childFields
                                                .asSequence()
                                                .map { (p: GQLOperationPath, f: Field) -> f }
                                                .toList()
                                        )
                                        .build()
                                )
                                .build()
                        )
                    }
                }
            }
            .partition { n: Node<*> -> n !is FragmentDefinition }
            .fold { selections: List<Node<*>>, fragmentDefinitions: List<Node<*>> ->
                parentPath to
                    TraversedFieldContext(
                        selectionSet =
                            SelectionSet.newSelectionSet()
                                .selections(
                                    selections
                                        .asSequence()
                                        .filterIsInstance<Selection<*>>()
                                        .plus(
                                            fragmentDefinitions
                                                .asSequence()
                                                .filterIsInstance<FragmentDefinition>()
                                                .map { fd: FragmentDefinition ->
                                                    FragmentSpread.newFragmentSpread()
                                                        .name(fd.name)
                                                        .build()
                                                }
                                        )
                                        .toList()
                                )
                                .build(),
                        fragmentDefinitions =
                            fragmentDefinitions
                                .asSequence()
                                .filterIsInstance<FragmentDefinition>()
                                .toPersistentList()
                    )
            }
    }

    private fun interface DocumentTraverser :
        (Map<GQLOperationPath, TraversedFieldContext>) -> Map<
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
                                .variableDefinitions(dtc.variableDefinitions)
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
        val argumentPathsByParentFieldPath:
            PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
        val directivePathsByParentPath:
            PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>
    )

    private data class TraversedFieldContext(
        val selectionSet: SelectionSet = SelectionSet.newSelectionSet().build(),
        val variableDefinitions: PersistentList<VariableDefinition> = persistentListOf(),
        val fragmentDefinitions: PersistentList<FragmentDefinition> = persistentListOf()
    )

    private data class DocumentTraversalContext(
        val parentImplementingTypeByParentPath:
            PersistentMap<GQLOperationPath, GraphQLImplementingType>,
        val documentTraverser: DocumentTraverser,
    )
}
