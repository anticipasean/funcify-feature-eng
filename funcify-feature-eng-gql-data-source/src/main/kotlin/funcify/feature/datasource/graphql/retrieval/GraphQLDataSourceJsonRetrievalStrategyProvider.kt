package funcify.feature.datasource.graphql.retrieval

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategyProvider

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface GraphQLDataSourceJsonRetrievalStrategyProvider :
    DataSourceSpecificJsonRetrievalStrategyProvider<GraphQLSourceIndex> {}
