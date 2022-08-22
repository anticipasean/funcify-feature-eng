package funcify.feature.materializer.service

import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
interface MaterializationGraphVertexConnector {

    fun connectSchematicVertex(
        context: MaterializationGraphVertexContext<*>
    ): MaterializationGraphVertexContext<*> {
        return when (context) {
            is SourceRootMaterializationGraphVertexContext -> {
                connectSourceRootVertex(context)
            }
            is SourceJunctionMaterializationGraphVertexContext -> {
                connectSourceJunctionVertex(context)
            }
            is SourceLeafMaterializationGraphVertexContext -> {
                connectSourceLeafVertex(context)
            }
            is ParameterJunctionMaterializationGraphVertexContext -> {
                connectParameterJunctionVertex(context)
            }
            is ParameterLeafMaterializationGraphVertexContext -> {
                connectParameterLeafVertex(context)
            }
        }
    }

    fun connectSourceRootVertex(
        context: SourceRootMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun connectSourceJunctionVertex(
        context: SourceJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun connectSourceLeafVertex(
        context: SourceLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun connectParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun connectParameterLeafVertex(
        context: ParameterLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>
}
