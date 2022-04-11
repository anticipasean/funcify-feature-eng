package funcify.feature.datasource.graphql

import graphql.introspection.IntrospectionQuery


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiService {

    val hostName: String

    val port: UInt

    val serviceContextPath: String

    val metadataQuery: String
        get() = IntrospectionQuery.INTROSPECTION_QUERY

}