package funcify.feature.materializer.service

import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.newcontext.MaterializationGraphContext
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex

/**
 *
 * @author smccarron
 * @created 2022-10-09
 */
interface NewMaterializationGraphConnector {

    fun connectSchematicVertex(
        vertex: SchematicVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        return when (vertex) {
            is SourceRootVertex -> {
                connectSourceRootVertex(vertex, context)
            }
            is SourceJunctionVertex -> {
                connectSourceJunctionOrLeafVertex(vertex, context)
            }
            is SourceLeafVertex -> {
                connectSourceJunctionOrLeafVertex(vertex, context)
            }
            is ParameterJunctionVertex -> {
                connectParameterJunctionOrLeafVertex(vertex, context)
            }
            is ParameterLeafVertex -> {
                connectParameterJunctionOrLeafVertex(vertex, context)
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "unhandled vertex type: [ type: ${vertex::class.simpleName} ]"
                )
            }
        }
    }

    fun connectSourceRootVertex(
        vertex: SourceRootVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext

    fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext

    fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext
}
