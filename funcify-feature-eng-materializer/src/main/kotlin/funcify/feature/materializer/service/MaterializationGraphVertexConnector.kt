package funcify.feature.materializer.service

import funcify.feature.materializer.context.MaterializationGraphVertexContext
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
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
 * @created 2022-08-17
 */
interface MaterializationGraphVertexConnector {

    fun connectSchematicVertex(
        context: MaterializationGraphVertexContext<*>
    ): MaterializationGraphVertexContext<*> {
        return when (context.currentVertex) {
            is SourceRootVertex -> {
                @Suppress("UNCHECKED_CAST") //
                connectSourceRootVertex(
                    context as MaterializationGraphVertexContext<SourceRootVertex>
                )
            }
            is SourceJunctionVertex -> {
                @Suppress("UNCHECKED_CAST") //
                connectSourceJunctionOrLeafVertex(
                    context as MaterializationGraphVertexContext<SourceJunctionVertex>
                )
            }
            is SourceLeafVertex -> {
                @Suppress("UNCHECKED_CAST") //
                connectSourceJunctionOrLeafVertex(
                    context as MaterializationGraphVertexContext<SourceLeafVertex>
                )
            }
            is ParameterJunctionVertex -> {
                @Suppress("UNCHECKED_CAST") //
                connectParameterJunctionOrLeafVertex(
                    context as MaterializationGraphVertexContext<ParameterJunctionVertex>
                )
            }
            is ParameterLeafVertex -> {
                @Suppress("UNCHECKED_CAST") //
                connectParameterJunctionOrLeafVertex(
                    context as MaterializationGraphVertexContext<ParameterLeafVertex>
                )
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "unhandled vertex type: [ type: ${context.currentVertex::class.simpleName} ]"
                )
            }
        }
    }

    fun connectSourceRootVertex(
        context: MaterializationGraphVertexContext<SourceRootVertex>
    ): MaterializationGraphVertexContext<*>

    fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        context: MaterializationGraphVertexContext<V>
    ): MaterializationGraphVertexContext<*>

    fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        context: MaterializationGraphVertexContext<V>
    ): MaterializationGraphVertexContext<*>
}
