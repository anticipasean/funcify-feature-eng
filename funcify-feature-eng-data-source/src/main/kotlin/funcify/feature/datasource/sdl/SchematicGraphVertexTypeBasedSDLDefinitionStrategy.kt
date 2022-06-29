package funcify.feature.datasource.sdl

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.vertex.SchematicGraphVertexType
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-06-29
 */
interface SchematicGraphVertexTypeBasedSDLDefinitionStrategy {

    val applicableSchematicGraphVertexTypes: ImmutableSet<SchematicGraphVertexType>

    fun isApplicableToVertex(vertex: SchematicVertex): Boolean {
        return SchematicGraphVertexType.getSchematicGraphTypeForVertexSubtype(vertex::class)
            .isDefined()
    }
}
