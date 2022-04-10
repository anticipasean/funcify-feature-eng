package funcify.feature.datasource.graphql.metadata

import funcify.feature.fetcher.metadata.DataFetcherMetadataProvider
import graphql.schema.GraphQLSchema


/**
 *
 * @author smccarron
 * @created 4/4/22
 */
interface GraphQLFetcherMetadataProvider : DataFetcherMetadataProvider<GraphQLSchema> {


}