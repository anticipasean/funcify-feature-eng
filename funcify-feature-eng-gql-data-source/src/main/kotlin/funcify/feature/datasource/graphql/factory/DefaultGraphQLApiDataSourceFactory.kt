package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.provider.GraphQLApiSourceMetadataProvider
import funcify.feature.datasource.graphql.metadata.reader.GraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLSchema
import java.time.Duration
import kotlin.reflect.KClass
import org.slf4j.Logger
import reactor.core.scheduler.Schedulers
import reactor.kotlin.extra.retry.retryExponentialBackoff

internal class DefaultGraphQLApiDataSourceFactory(
    private val graphQLApiSourceMetadataProvider: GraphQLApiSourceMetadataProvider,
    private val graphQLApiSourceMetadataReader: GraphQLApiSourceMetadataReader
) : GraphQLApiDataSourceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLApiServiceFactory>()

        internal data class DefaultGraphQLApiDataSourceKey(
            override val name: String,
        ) : DataSource.Key<GraphQLSourceIndex> {
            override val dataSourceType: DataSourceType = RawDataSourceType.GRAPHQL_API
            override val sourceIndexType: KClass<GraphQLSourceIndex> = GraphQLSourceIndex::class
        }

        internal data class DefaultGraphQLApiDataSource(
            override val name: String,
            override val graphQLApiService: GraphQLApiService,
            override val graphQLSourceSchema: GraphQLSchema,
            override val sourceMetamodel: SourceMetamodel<GraphQLSourceIndex>,
            override val key: DataSource.Key<GraphQLSourceIndex>
        ) : GraphQLApiDataSource
    }

    override fun createGraphQLApiDataSource(
        name: String,
        graphQLApiService: GraphQLApiService
    ): GraphQLApiDataSource {
        logger.info("create_graphql_api_data_source: [ name: $name ]")
        return graphQLApiSourceMetadataProvider
            .provideMetadata(graphQLApiService)
            .cache()
            .retryExponentialBackoff(times = 3, first = Duration.ofSeconds(4))
            .subscribeOn(Schedulers.immediate())
            .onErrorMap { t: Throwable ->
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    """error occurred when retrieving metadata 
                        |from graphql_api_data_source""".flatten(),
                    t
                )
            }
            .blockOptional()
            .toOption()
            .successIfDefined {
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    """no metadata received [ type: ${GraphQLSchema::class.qualifiedName} ]
                        |from graphql_api_data_source""".flatten(),
                )
            }
            .map { gqlSchema: GraphQLSchema ->
                val dataSourceKey = DefaultGraphQLApiDataSourceKey(name = name)
                DefaultGraphQLApiDataSource(
                    name = name,
                    graphQLApiService = graphQLApiService,
                    graphQLSourceSchema = gqlSchema,
                    sourceMetamodel =
                        graphQLApiSourceMetadataReader.readSourceMetamodelFromMetadata(
                            dataSourceKey,
                            gqlSchema
                        ),
                    key = dataSourceKey
                )
            }
            .orElseThrow()
    }
}
