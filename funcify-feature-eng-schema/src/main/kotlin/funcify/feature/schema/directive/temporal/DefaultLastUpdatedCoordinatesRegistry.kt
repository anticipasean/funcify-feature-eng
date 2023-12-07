package funcify.feature.schema.directive.temporal

import arrow.core.Option
import arrow.core.foldLeft
import arrow.core.getOrNone
import arrow.core.lastOrNone
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectedField
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @author smccarron
 * @created 2022-07-25
 */
internal data class DefaultLastUpdatedCoordinatesRegistry(
    private val lastUpdatedCoordinatesByPath: PersistentMap<GQLOperationPath, FieldCoordinates> =
        persistentMapOf()
) : LastUpdatedCoordinatesRegistry {

    private val lastUpdatedFieldPathsSet: PersistentSet<GQLOperationPath> by lazy {
        lastUpdatedCoordinatesByPath.keys.toPersistentSet()
    }

    private val lastUpdatedFieldPathByParentPath:
        PersistentMap<GQLOperationPath, GQLOperationPath> by lazy {
        lastUpdatedCoordinatesByPath
            .asSequence()
            .map { (path: GQLOperationPath, _: FieldCoordinates) ->
                path.getParentPath().map { pp: GQLOperationPath -> pp to path }
            }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    private val nearestLastUpdatedTemporalAttributeRelativeMemoizer:
        (GQLOperationPath) -> Option<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, GQLOperationPath> = ConcurrentHashMap();
        { path: GQLOperationPath ->
            cache.computeIfAbsent(path, nearestLastUpdatedFieldRelativeCalculator()).toOption()
        }
    }

    private val computedStringForm: String by lazy {
        sequenceOf<Pair<String, JsonNode>>(
                "last_updated_coordinates_by_path" to
                    lastUpdatedCoordinatesByPath.foldLeft(
                        JsonNodeFactory.instance.arrayNode(lastUpdatedCoordinatesByPath.size)
                    ) { an: ArrayNode, (sp: GQLOperationPath, fc: FieldCoordinates) ->
                        an.add(
                            JsonNodeFactory.instance
                                .objectNode()
                                .put("path", sp.toString())
                                .put("coordinates", fc.toString())
                        )
                    },
                "last_updated_field_path_by_parent_path" to
                    lastUpdatedFieldPathByParentPath.asSequence().fold(
                        JsonNodeFactory.instance.objectNode()
                    ) { on: ObjectNode, (pp: GQLOperationPath, cp: GQLOperationPath) ->
                        on.put(pp.toString(), cp.toString())
                    }
            )
            .fold(JsonNodeFactory.instance.objectNode()) {
                on: ObjectNode,
                (key: String, value: JsonNode) ->
                on.set<ObjectNode>(key, value)
            }
            .let { on: ObjectNode ->
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(on)
            }
    }

    override fun toString(): String {
        return computedStringForm
    }

    override fun pathBelongsToLastUpdatedField(path: GQLOperationPath): Boolean {
        return path in lastUpdatedFieldPathsSet
    }

    override fun getAllPathsBelongingToLastUpdatedFields(): ImmutableSet<GQLOperationPath> {
        return lastUpdatedFieldPathsSet
    }

    override fun pathBelongsToParentOfLastUpdatedField(path: GQLOperationPath): Boolean {
        return path in lastUpdatedFieldPathByParentPath
    }

    override fun registerLastUpdatedField(
        path: GQLOperationPath,
        coordinates: FieldCoordinates
    ): LastUpdatedCoordinatesRegistry {
        require(!path.isRoot() && path.refersToSelection()) {
            "path must refer to a field, not an argument or directive"
        }
        require(
            path.selection
                .lastOrNone()
                .map { ss: SelectionSegment ->
                    when (ss) {
                        is AliasedFieldSegment -> ss
                        is FieldSegment -> ss
                        is FragmentSpreadSegment -> ss.selectedField
                        is InlineFragmentSegment -> ss.selectedField
                    }
                }
                .filter { sf: SelectedField ->
                    when (sf) {
                        is AliasedFieldSegment -> false
                        is FieldSegment -> sf.fieldName == coordinates.fieldName
                    }
                }
                .isDefined()
        ) {
            "path does not refer to same field referenced in coordinates"
        }
        return DefaultLastUpdatedCoordinatesRegistry(
            lastUpdatedCoordinatesByPath = lastUpdatedCoordinatesByPath.plus(path to coordinates)
        )
    }

    override fun findNearestLastUpdatedField(
        path: GQLOperationPath
    ): Option<Pair<GQLOperationPath, FieldCoordinates>> {
        return nearestLastUpdatedTemporalAttributeRelativeMemoizer.invoke(path).flatMap {
            p: GQLOperationPath ->
            lastUpdatedCoordinatesByPath.getOrNone(p).map { fc: FieldCoordinates -> p to fc }
        }
    }

    private fun nearestLastUpdatedFieldRelativeCalculator():
        (GQLOperationPath) -> GQLOperationPath? {
        return { path: GQLOperationPath ->
            when {
                path.isRoot() -> {
                    null
                }
                path in lastUpdatedFieldPathsSet -> {
                    path
                }
                else -> {
                    Option(path)
                        .recurse { p: GQLOperationPath ->
                            p.getParentPath().flatMap { pp: GQLOperationPath ->
                                when {
                                    pp in lastUpdatedFieldPathByParentPath -> {
                                        lastUpdatedFieldPathByParentPath
                                            .getOrNone(pp)
                                            .map(GQLOperationPath::right)
                                    }
                                    else -> {
                                        pp.some().map(GQLOperationPath::left)
                                    }
                                }
                            }
                        }
                        .orNull()
                }
            }
        }
    }
}
