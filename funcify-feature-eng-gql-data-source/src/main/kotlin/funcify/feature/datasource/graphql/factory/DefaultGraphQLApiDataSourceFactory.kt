package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.GraphQLFetcherMetadataProvider
import funcify.feature.datasource.graphql.reader.GraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class DefaultGraphQLApiDataSourceFactory(
    private val graphQLFetcherMetadataProvider: GraphQLFetcherMetadataProvider,
    private val graphQLMetadataReader: GraphQLApiSourceMetadataReader
) : GraphQLApiDataSourceFactory {

    companion object {
        private val logger: Logger =
            LoggerFactory.getLogger(DefaultGraphQLApiServiceFactory::class.java)

        internal data class DefaultGraphQLApiDataSource(
            override val name: String,
            override val graphQLApiService: GraphQLApiService,
            override val graphQLSourceSchema: GraphQLSchema,
            override val graphQLSourceMetamodel: GraphQLSourceMetamodel
        ) : GraphQLApiDataSource {}
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
                    graphQLSourceMetamodel =
                        graphQLMetadataReader.readSourceMetamodelFromMetadata(gqlSchema) as?
                            GraphQLSourceMetamodel
                            ?: throw GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                "not of type graphql_source_metamodel"
                            )
                )
            }
            .blockFirstOrElseThrow()
    }
}
