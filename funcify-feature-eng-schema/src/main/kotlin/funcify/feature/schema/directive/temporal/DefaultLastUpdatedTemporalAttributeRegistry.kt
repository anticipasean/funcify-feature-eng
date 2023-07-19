package funcify.feature.schema.directive.temporal

import arrow.core.Option
import arrow.core.getOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * @author smccarron
 * @created 2022-07-25
 */
internal data class DefaultLastUpdatedTemporalAttributeRegistry(
    private val lastUpdatedTemporalAttributePathsSet: PersistentSet<SchematicPath> =
        persistentSetOf()
                                                               ) : LastUpdatedTemporalAttributeRegistry {

    private val lastUpdatedTemporalAttributePathByParentPath:
        PersistentMap<SchematicPath, SchematicPath> by lazy {
        lastUpdatedTemporalAttributePathsSet
            .asSequence()
            .map { attrPath -> attrPath.getParentPath().map { pp -> pp to attrPath } }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    private val nearestLastUpdatedTemporalAttributeRelativeMemoizer:
        (SchematicPath) -> Option<SchematicPath> by lazy {
        val cache: ConcurrentMap<SchematicPath, SchematicPath> = ConcurrentHashMap()
        ({ path: SchematicPath ->
            cache
                .computeIfAbsent(path, nearestLastUpdatedTemporalAttributeRelativeCalculator())
                .toOption()
        })
    }

    private val computedStringForm: String by lazy {
        sequenceOf<Pair<String, JsonNode>>(
                "last_updated_temporal_attribute_paths" to
                    lastUpdatedTemporalAttributePathsSet.fold(
                        JsonNodeFactory.instance.arrayNode(
                            lastUpdatedTemporalAttributePathsSet.size
                        )
                    ) { an: ArrayNode, sp: SchematicPath ->
                        an.add(sp.toString())
                    },
                "last_updated_temporal_attribute_path_by_parent_path" to
                    lastUpdatedTemporalAttributePathByParentPath.asSequence().fold(
                        JsonNodeFactory.instance.objectNode()
                    ) { on: ObjectNode, (pp: SchematicPath, cp: SchematicPath) ->
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

    override fun registerSchematicPathAsMappingToLastUpdatedTemporalAttributeVertex(
        path: SchematicPath
    ): LastUpdatedTemporalAttributeRegistry {
        return DefaultLastUpdatedTemporalAttributeRegistry(
            lastUpdatedTemporalAttributePathsSet = lastUpdatedTemporalAttributePathsSet.add(path)
                                                          )
    }

    override fun pathBelongsToLastUpdatedTemporalAttributeVertex(path: SchematicPath): Boolean {
        return path in lastUpdatedTemporalAttributePathsSet
    }

    override fun getAllPathsBelongingToLastUpdatedTemporalAttributeVertices():
        ImmutableSet<SchematicPath> {
        return lastUpdatedTemporalAttributePathsSet
    }

    override fun pathBelongsToLastUpdatedTemporalAttributeParentVertex(
        path: SchematicPath
    ): Boolean {
        return path in lastUpdatedTemporalAttributePathByParentPath
    }

    override fun getLastUpdatedTemporalAttributeChildPathOfParentPath(
        path: SchematicPath
    ): Option<SchematicPath> {
        return lastUpdatedTemporalAttributePathByParentPath.getOrNone(path)
    }

    override fun findNearestLastUpdatedTemporalAttributePathRelative(
        path: SchematicPath
    ): Option<SchematicPath> {
        return nearestLastUpdatedTemporalAttributeRelativeMemoizer(path)
    }

    private fun nearestLastUpdatedTemporalAttributeRelativeCalculator():
        (SchematicPath) -> SchematicPath? {
        return { path: SchematicPath ->
            when {
                path.isRoot() -> {
                    none()
                } // closest relative is itself
                path in lastUpdatedTemporalAttributePathsSet -> {
                    path.some()
                }
                else -> {
                    path
                        .some()
                        .filter { p -> p.argument.isEmpty() && p.directive.isEmpty() }
                        .orElse { path.transform { clearArgument().clearDirective() }.some() }
                        .recurse { p ->
                            p.getParentPath()
                                .flatMap { pp
                                    -> // sibling if parent is the same, cousin if grandparent is
                                    // the
                                    // same,
                                    // etc.
                                    lastUpdatedTemporalAttributePathByParentPath[pp].toOption()
                                }
                                .fold(
                                    { p.getParentPath().map { pp -> pp.left() } },
                                    { lastUpdatedTemporalAttributePath ->
                                        lastUpdatedTemporalAttributePath.right().some()
                                    }
                                )
                        }
                }
            }.orNull()
        }
    }
}
