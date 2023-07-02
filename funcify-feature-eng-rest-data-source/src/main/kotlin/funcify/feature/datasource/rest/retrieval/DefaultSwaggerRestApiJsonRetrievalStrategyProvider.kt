package funcify.feature.datasource.rest.retrieval

import arrow.core.Either
import funcify.feature.datasource.rest.RestApiDataElementSource
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.tools.json.JsonMapper
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

internal class DefaultSwaggerRestApiJsonRetrievalStrategyProvider(
    private val jsonMapper: JsonMapper,
    private val postProcessingStrategy: SwaggerRestApiJsonResponsePostProcessingStrategy
) : SwaggerRestApiJsonRetrievalStrategyProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestApiJsonRetrievalStrategyProvider>()
    }

    override fun providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean {
        return dataSourceKey.sourceIndexType.isSubclassOf(RestApiSourceIndex::class)
    }

    override fun createExternalDataSourceJsonValuesRetrieverFor(
        dataSource: DataElementSource<RestApiSourceIndex>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    ): Try<DataElementJsonValueSource> {
        logger.debug(
            """create_schematic_path_based_json_retrieval_function_for: [ 
            |data_source: ${dataSource.key}, 
            |source_vertices.size: ${sourceVertices.size}, 
            |parameter_vertices.size: ${parameterVertices.size} 
            |]""".flatten()
        )
        return Try.attempt {
            DefaultSwaggerRestDataElementJsonRetrievalStrategy(
                jsonMapper = jsonMapper,
                dataSource = dataSource as RestApiDataElementSource,
                parameterVertices = parameterVertices,
                sourceVertices = sourceVertices,
                postProcessingStrategy = postProcessingStrategy
                                                              )
        }
    }
}
