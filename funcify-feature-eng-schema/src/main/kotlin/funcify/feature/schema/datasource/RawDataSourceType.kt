package funcify.feature.schema.datasource


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
enum class RawDataSourceType : DataSourceType {

    REST_API,
    GRAPHQL_API,
    RELATIONAL_DATABASE,
    NOSQL_DATABASE,
    TIMESERIES_DATABASE,
    TRANSFORMER;


}