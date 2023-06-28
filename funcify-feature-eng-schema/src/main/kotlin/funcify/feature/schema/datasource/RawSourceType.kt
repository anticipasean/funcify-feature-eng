package funcify.feature.schema.datasource


/**
 *  Default implementation of data source type
 * @author smccarron
 * @created 1/30/22
 */
enum class RawSourceType : SourceType {

    REST_API,
    GRAPHQL_API,
    RELATIONAL_DATABASE,
    NOSQL_DATABASE,
    TIMESERIES_DATABASE,
    TRANSFORMER;


}
