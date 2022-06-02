package funcify.feature.materializer.configuration

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.request.DefaultRawGraphQLRequestFactory
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MaterializerConfiguration {

    companion object {
        private val logger: Logger = loggerFor<MaterializerConfiguration>()
    }

    @ConditionalOnMissingBean(value = [MetamodelGraph::class])
    @Bean
    fun metamodelGraph(
        metamodelGraphFactory: MetamodelGraphFactory,
        dataSources: List<DataSource<*>>
    ): MetamodelGraph {
        return dataSources
            .fold(metamodelGraphFactory.builder()) { bldr, ds -> bldr.addDataSource(ds) }
            .build()
            .peek(
                { mmg: MetamodelGraph ->
                    val firstVertexPath: String =
                        mmg.toOption()
                            .filter { m -> m.vertices.size > 0 }
                            .map { m -> m.vertices[0].path.toString() }
                            .getOrElse { "<NA>" }
                    logger.info(
                        """metamodel_graph: [ status: success ] 
                            |[ metamodel_graph [ vertices.size: ${mmg.vertices.size}, 
                            |vertices[0].path: $firstVertexPath ] ]
                            |""".flattenIntoOneLine()
                    )
                },
                { t: Throwable ->
                    logger.error(
                        """metamodel_graph: [ status: failed ] 
                            |[ type: ${t::class.simpleName}, 
                            |message: ${t.message} ]
                            |""".flattenIntoOneLine()
                    )
                }
            )
            .orElseThrow { t: Throwable ->
                when (t) {
                    is FeatureEngCommonException -> t
                    else -> {
                        MaterializerException(
                            MaterializerErrorResponse.METAMODEL_GRAPH_CREATION_ERROR,
                            t
                        )
                    }
                }
            }
    }

    @ConditionalOnMissingBean(value = [RawGraphQLRequestFactory::class])
    @Bean
    fun rawGraphQLRequestFactory(): RawGraphQLRequestFactory {
        return DefaultRawGraphQLRequestFactory()
    }
}
