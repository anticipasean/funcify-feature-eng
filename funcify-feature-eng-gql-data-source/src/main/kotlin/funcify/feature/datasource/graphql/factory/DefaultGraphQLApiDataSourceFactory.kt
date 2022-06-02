package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.GraphQLFetcherMetadataProvider
import funcify.feature.datasource.graphql.reader.GraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLSchema
import org.slf4j.Logger

internal class DefaultGraphQLApiDataSourceFactory(
    private val graphQLFetcherMetadataProvider: GraphQLFetcherMetadataProvider,
    private val graphQLMetadataReader: GraphQLApiSourceMetadataReader
) : GraphQLApiDataSourceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLApiServiceFactory>()

        internal data class DefaultGraphQLApiDataSourceKey(
            override val name: String,
            override val sourceType: DataSourceType
        ) : DataSource.Key<GraphQLSourceIndex> {}

        internal data class DefaultGraphQLApiDataSource(
            override val name: String,
            override val graphQLApiService: GraphQLApiService,
            override val graphQLSourceSchema: GraphQLSchema,
            override val sourceMetamodel: SourceMetamodel<GraphQLSourceIndex>
        ) : GraphQLApiDataSource {
            override val key: DataSource.Key<GraphQLSourceIndex> by lazy {
                DefaultGraphQLApiDataSourceKey(name, sourceType)
            }
        }
    }

    override fun createGraphQLApiDataSource(
        name: String,
        graphQLApiService: GraphQLApiService
    ): GraphQLApiDataSource {
        logger.info("create_graphql_api_data_source: [ name: $name ]")
        return graphQLFetcherMetadataProvider
            .provideMetadata(graphQLApiService)
            .map { gqlSchema: GraphQLSchema ->
                DefaultGraphQLApiDataSource(
                    name = name,
                    graphQLApiService = graphQLApiService,
                    graphQLSourceSchema = gqlSchema,
                    sourceMetamodel =
                        graphQLMetadataReader.readSourceMetamodelFromMetadata(gqlSchema)
                )
            }
            .blockForFirst()
            .orElseThrow { t: Throwable ->
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    """error occurred when retrieving or processing metadata 
                        |from graphql_api_data_source""".flattenIntoOneLine(),
                    t
                )
            }
    }
}
