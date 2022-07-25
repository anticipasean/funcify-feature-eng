package funcify.feature.schema.directive.temporal

import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
internal data class DefaultLastUpdatedTemporalAttributePathRegistry(
    private val lastUpdatedTemporalAttributePathsSet: PersistentSet<SchematicPath> = persistentSetOf()
) : LastUpdatedTemporalAttributePathRegistry {

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
}
