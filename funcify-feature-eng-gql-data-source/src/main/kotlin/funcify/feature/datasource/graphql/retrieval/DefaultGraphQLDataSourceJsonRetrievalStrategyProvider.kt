package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.retrieval.ExternalDataSourceJsonValuesRetriever
import funcify.feature.json.JsonMapper
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.concurrent.Executor
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

internal class DefaultGraphQLDataSourceJsonRetrievalStrategyProvider(
    private val asyncExecutor: Executor,
    private val jsonMapper: JsonMapper
) : GraphQLDataSourceJsonRetrievalStrategyProvider {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultGraphQLDataSourceJsonRetrievalStrategyProvider>()
    }

    override fun providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean {
        return dataSourceKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class)
    }

    override fun createExternalDataSourceJsonValuesRetrieverFor(
        dataSource: DataSource<GraphQLSourceIndex>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    ): Try<ExternalDataSourceJsonValuesRetriever> {
        logger.debug(
            """create_schematic_path_based_json_retrieval_function_for: [ 
            |data_source: ${dataSource.key}, 
            |source_vertices.size: ${sourceVertices.size}, 
            |parameter_vertices.size: ${parameterVertices.size} 
            |]""".flatten()
        )
        return Try.attempt {
            DefaultGraphQLDataSourceJsonRetrievalStrategy(
                asyncExecutor = asyncExecutor,
                jsonMapper = jsonMapper,
                graphQLDataSource = dataSource as GraphQLApiDataSource,
                parameterVertices = parameterVertices,
                sourceVertices = sourceVertices
            )
        }
    }
}
