package funcify.feature.schema.directive.temporal

import arrow.core.Option
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterContainerTypeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
interface LastUpdatedAttributeRegistry {

    fun registerLastUpdatedTemporalSourceAttributeVertexOnSourceContainerTypeVertex(
        sourceAttributeVertex: SourceAttributeVertex,
        sourceContainerTypeVertex: SourceContainerTypeVertex
    ): LastUpdatedAttributeRegistry

    fun registerLastUpdatedTemporalParameterAttributeVertexOnParameterContainerTypeVertex(
        parameterAttributeVertex: ParameterAttributeVertex,
        parameterContainerTypeVertex: ParameterContainerTypeVertex
    ): LastUpdatedAttributeRegistry

    fun hasLastUpdatedTemporalAttributeVertex(
        sourceContainerTypeVertex: SourceContainerTypeVertex
    ): Boolean {
        return getLastUpdatedTemporalAttributeVertexOnSourceContainerTypeVertex(
                sourceContainerTypeVertex
            )
            .isDefined()
    }

    fun hasLastUpdatedTemporalAttributeVertex(
        parameterContainerTypeVertex: ParameterContainerTypeVertex
    ): Boolean {
        return getLastUpdatedTemporalAttributeVertexOnParameterContainerTypeVertex(
                parameterContainerTypeVertex
            )
            .isDefined()
    }

    fun getLastUpdatedTemporalAttributeVertexOnSourceContainerTypeVertex(
        sourceContainerTypeVertex: SourceContainerTypeVertex
    ): Option<SourceAttributeVertex>

    fun getLastUpdatedTemporalAttributeVertexOnParameterContainerTypeVertex(
        parameterContainerTypeVertex: ParameterContainerTypeVertex
    ): Option<ParameterAttributeVertex>

    fun pathBelongsToTemporalAttributeVertex(schematicPath: SchematicPath): Boolean {
        return getTemporalAttributeVertexBelongingToPath(schematicPath).isDefined()
    }

    fun getTemporalAttributeVertexBelongingToPath(
        schematicPath: SchematicPath
    ): Option<SchematicVertex>
}
