package funcify.feature.datasource.graphql.metadata.provider

import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.metadata.provider.DataSourceMetadataProvider
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 4/4/22
 */
interface GraphQLApiSourceMetadataProvider :
    DataSourceMetadataProvider<GraphQLApiService, GraphQLSchema>
