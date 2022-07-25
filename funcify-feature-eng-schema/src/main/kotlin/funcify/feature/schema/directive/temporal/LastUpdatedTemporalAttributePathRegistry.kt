package funcify.feature.schema.directive.temporal

import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
interface LastUpdatedTemporalAttributePathRegistry {

    companion object {

        @JvmStatic
        fun newRegistry(): LastUpdatedTemporalAttributePathRegistry {
            return DefaultLastUpdatedTemporalAttributePathRegistry()
        }
    }

    fun registerSchematicPathAsMappingToLastUpdatedTemporalAttributeVertex(
        path: SchematicPath
    ): LastUpdatedTemporalAttributePathRegistry

    fun pathBelongsToLastUpdatedTemporalAttributeVertex(path: SchematicPath): Boolean

    fun getAllPathsBelongingToLastUpdatedTemporalAttributeVertices(): ImmutableSet<SchematicPath>
}
