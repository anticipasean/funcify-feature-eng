package funcify.feature.schema.directive.identifier

import arrow.core.Option
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet

/**
 *
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
        path: SchematicPath
    ): EntityRegistry

    fun pathBelongsToEntityIdentifierAttributeVertex(path: SchematicPath): Boolean

    fun getAllPathsBelongingToEntityIdentifierAttributeVertices(): ImmutableSet<SchematicPath>

    fun pathBelongsToEntitySourceContainerTypeVertex(path: SchematicPath): Boolean

    fun getEntityIdentifierAttributeVerticesBelongingToSourceContainerIndexPath(
        path: SchematicPath
    ): ImmutableSet<SchematicPath>

    /**
     * Assumes entities could have _composite_ identifiers (=> more than one attribute is necessary
     * for identification of a specific instance)
     */
    fun findNearestEntityIdentifierPathRelatives(path: SchematicPath): ImmutableSet<SchematicPath>

    fun findNearestEntitySourceContainerTypeVertexAncestor(
        path: SchematicPath
    ): Option<SchematicPath>
}
