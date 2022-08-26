package funcify.feature.schema.directive.temporal

import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
internal data class DefaultLastUpdatedTemporalAttributePathRegistry(
    private val lastUpdatedTemporalAttributePathsSet: PersistentSet<SchematicPath> =
        persistentSetOf()
) : LastUpdatedTemporalAttributePathRegistry {

    private val lastUpdatedTemporalAttributePathByParentPath:
        PersistentMap<SchematicPath, SchematicPath> by lazy {
        lastUpdatedTemporalAttributePathsSet
            .asSequence()
            .map { attrPath -> attrPath.getParentPath().map { pp -> pp to attrPath } }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    override fun registerSchematicPathAsMappingToLastUpdatedTemporalAttributeVertex(
        path: SchematicPath
    ): LastUpdatedTemporalAttributePathRegistry {
        return copy(
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

    override fun findNearestLastUpdatedTemporalAttributePathRelative(
        path: SchematicPath
    ): Option<SchematicPath> {
        return when {
            path.isRoot() -> {
                none()
            }
            // closest relative is itself
            path in lastUpdatedTemporalAttributePathsSet -> {
                path.some()
            }
            else -> {
                path
                    .some()
                    .filter { p -> p.arguments.isEmpty() && p.directives.isEmpty() }
                    .orElse { path.transform { clearArguments().clearDirectives() }.some() }
                    .recurse { p ->
                        p.getParentPath()
                            .flatMap { pp ->
                                // sibling if parent is the same, cousin if grandparent is the same,
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
        }
    }
}
