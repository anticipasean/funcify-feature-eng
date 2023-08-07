package funcify.feature.schema.directive.temporal

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableSet

/**
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
        path: GQLOperationPath
    ): LastUpdatedTemporalAttributeRegistry

    fun pathBelongsToLastUpdatedTemporalAttributeVertex(path: GQLOperationPath): Boolean

    fun getAllPathsBelongingToLastUpdatedTemporalAttributeVertices(): ImmutableSet<GQLOperationPath>

    fun pathBelongsToLastUpdatedTemporalAttributeParentVertex(path: GQLOperationPath): Boolean

    fun getLastUpdatedTemporalAttributeChildPathOfParentPath(
        path: GQLOperationPath
    ): Option<GQLOperationPath>

    fun findNearestLastUpdatedTemporalAttributePathRelative(
        path: GQLOperationPath
    ): Option<GQLOperationPath>
}
