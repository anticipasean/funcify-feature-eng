package funcify.feature.datasource.rest.retrieval

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.dataelementsource.retrieval.DataSourceRepresentativeJsonRetrievalStrategyProvider

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface SwaggerRestApiJsonRetrievalStrategyProvider : DataSourceRepresentativeJsonRetrievalStrategyProvider<RestApiSourceIndex> {}
