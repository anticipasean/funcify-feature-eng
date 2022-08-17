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

    fun onSourceRootVertex(
        context: SourceRootMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun onSourceJunctionVertex(
        context: SourceJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun onSourceLeafVertex(
        context: SourceLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun onParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

    fun onParameterLeafVertex(
        context: ParameterLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*>

}
