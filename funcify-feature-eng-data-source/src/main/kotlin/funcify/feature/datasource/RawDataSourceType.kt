package funcify.feature.datasource

import funcify.feature.schema.datasource.DataSourceType


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
enum class RawDataSourceType : DataSourceType {

    REST_API,
    GRAPHQL,
    RELATIONAL_DATABASE,
    NOSQL_DATABASE,
    TIMESERIES_DATABASE,
    TRANSFORMER;


}