package funcify.feature.datasource.rest.retrieval

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategyProvider

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface SwaggerRestApiJsonRetrievalStrategyProvider :
    DataSourceSpecificJsonRetrievalStrategyProvider<RestApiSourceIndex> {}
