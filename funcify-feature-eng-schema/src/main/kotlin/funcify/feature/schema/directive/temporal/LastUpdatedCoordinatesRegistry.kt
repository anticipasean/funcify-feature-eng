package funcify.feature.schema.directive.temporal

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2022-07-25
 */
interface LastUpdatedCoordinatesRegistry {

    companion object {

        @JvmStatic
        fun newRegistry(): LastUpdatedCoordinatesRegistry {
            return DefaultLastUpdatedCoordinatesRegistry()
        }
    }

    fun registerLastUpdatedField(
        path: GQLOperationPath,
        coordinates: FieldCoordinates
    ): LastUpdatedCoordinatesRegistry

    fun pathBelongsToLastUpdatedField(path: GQLOperationPath): Boolean

    fun getAllPathsBelongingToLastUpdatedFields(): ImmutableSet<GQLOperationPath>

    fun pathBelongsToParentOfLastUpdatedField(path: GQLOperationPath): Boolean

    fun findNearestLastUpdatedField(
        path: GQLOperationPath
    ): Option<Pair<GQLOperationPath, FieldCoordinates>>
}
