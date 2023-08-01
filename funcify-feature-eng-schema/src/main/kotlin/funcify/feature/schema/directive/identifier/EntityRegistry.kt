package funcify.feature.schema.directive.identifier

import arrow.core.Option
import funcify.feature.schema.path.GQLOperationPath
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2022-09-15
 */
interface EntityRegistry {

    companion object {

        @JvmStatic
        fun newRegistry(): EntityRegistry {
            return DefaultEntityRegistry()
        }
    }

    fun registerSchematicPathAsMappingToEntityIdentifierAttributeVertex(
        path: GQLOperationPath
    ): EntityRegistry

    fun pathBelongsToEntityIdentifierAttributeVertex(path: GQLOperationPath): Boolean

    fun getAllPathsBelongingToEntityIdentifierAttributeVertices(): ImmutableSet<GQLOperationPath>

    fun pathBelongsToEntitySourceContainerTypeVertex(path: GQLOperationPath): Boolean

    fun getEntityIdentifierAttributeVerticesBelongingToSourceContainerIndexPath(
        path: GQLOperationPath
    ): ImmutableSet<GQLOperationPath>

    /**
     * Assumes entities could have _composite_ identifiers (=> more than one attribute is necessary
     * for identification of a specific instance)
     */
    fun findNearestEntityIdentifierPathRelatives(
        path: GQLOperationPath
    ): ImmutableSet<GQLOperationPath>

    fun findNearestEntitySourceContainerTypeVertexAncestor(
        path: GQLOperationPath
    ): Option<GQLOperationPath>
}
