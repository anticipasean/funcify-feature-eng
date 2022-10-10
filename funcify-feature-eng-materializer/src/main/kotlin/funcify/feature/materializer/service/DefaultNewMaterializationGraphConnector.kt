package funcify.feature.materializer.service

import funcify.feature.materializer.newcontext.MaterializationGraphContext
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-10-09
 */
class DefaultNewMaterializationGraphConnector : NewMaterializationGraphConnector {

    companion object {
        private val logger: Logger = loggerFor<DefaultNewMaterializationGraphConnector>()
    }
    override fun connectSourceRootVertex(
        vertex: SourceRootVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info("connect_source_root_vertex: [ vertex.path: {} ]", vertex.path)
        TODO("Not yet implemented")
    }

    override fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        logger.info("connect_source_junction_or_leaf_vertex: [ vertex.path: {} ]", vertex.path)
        TODO("Not yet implemented")
    }

    override fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        logger.info("connect_parameter_junction_or_leaf_vertex: [ vertex.path: {} ]", vertex.path)
        TODO("Not yet implemented")
    }
}
