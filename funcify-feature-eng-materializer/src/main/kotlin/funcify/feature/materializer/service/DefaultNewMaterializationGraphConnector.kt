package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.getOrElse
import funcify.feature.materializer.newcontext.MaterializationGraphContext
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.language.Argument
import graphql.language.Field
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
        field: Option<Field>,
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info(
            "connect_source_junction_or_leaf_vertex: [ field.name: {}, vertex.path: {} ]",
            field.map { f -> f.name }.getOrElse { "<NA>" },
            vertex.path
        )
        TODO("Not yet implemented")
    }

    override fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        argument: Option<Argument>,
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info(
            "connect_parameter_junction_or_leaf_vertex: [ argument.name: {}, vertex.path: {} ]",
            argument.map { a -> a.name }.getOrElse { "<NA>" },
            vertex.path
        )
        TODO("Not yet implemented")
    }
}
