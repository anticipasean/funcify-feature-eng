package funcify.feature.schema.directive.temporal

import arrow.core.Option
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
interface LastUpdatedTemporalAttributeRegistry {

    companion object {

        @JvmStatic
        fun newRegistry(): LastUpdatedTemporalAttributeRegistry {
            return DefaultLastUpdatedTemporalAttributeRegistry()
        }
    }

    fun registerSchematicPathAsMappingToLastUpdatedTemporalAttributeVertex(
        path: SchematicPath
    ): LastUpdatedTemporalAttributeRegistry

    fun pathBelongsToLastUpdatedTemporalAttributeVertex(path: SchematicPath): Boolean

    fun getAllPathsBelongingToLastUpdatedTemporalAttributeVertices(): ImmutableSet<SchematicPath>

    fun pathBelongsToLastUpdatedTemporalAttributeParentVertex(path: SchematicPath): Boolean

    fun getLastUpdatedTemporalAttributeChildPathOfParentPath(path: SchematicPath): Option<SchematicPath>

    fun findNearestLastUpdatedTemporalAttributePathRelative(path: SchematicPath): Option<SchematicPath>
}
