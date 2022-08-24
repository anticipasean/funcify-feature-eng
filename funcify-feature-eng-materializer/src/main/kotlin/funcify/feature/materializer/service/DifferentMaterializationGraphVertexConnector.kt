package funcify.feature.materializer.service

import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-23
 */
internal class DifferentMaterializationGraphVertexConnector : MaterializationGraphVertexConnector {

    companion object {
        private val logger: Logger = loggerFor<DifferentMaterializationGraphVertexConnector>()
    }

    override fun connectSourceRootVertex(
        context: SourceRootMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.info("connect_source_root_vertex: [ context.path: ${context.path} ]")
        TODO("Not yet implemented")
    }

    override fun connectSourceJunctionVertex(
        context: SourceJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.info("connect_source_junction_vertex: [ context.path: ${context.path} ]")
        TODO("Not yet implemented")
    }

    override fun connectSourceLeafVertex(
        context: SourceLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.info("connect_source_leaf_vertex: [ context.path: ${context.path} ]")
        TODO("Not yet implemented")
    }

    override fun connectParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.info("connect_parameter_junction_vertex: [ context.path: ${context.path} ]")
        TODO("Not yet implemented")
    }

    override fun connectParameterLeafVertex(
        context: ParameterLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.info("connect_parameter_leaf_vertex: [ context.path: ${context.path} ]")
        TODO("Not yet implemented")
    }
}
