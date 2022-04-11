package funcify.feature.datasource.graphql

import funcify.feature.datasource.graphql.factory.GraphQLApiServiceFactory
import graphql.introspection.IntrospectionQuery


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiService {

    companion object {

        fun builder(): Builder {
            return GraphQLApiServiceFactory.DefaultGraphQLApiServiceBuilder()
        }
    }

    val serviceName: String

    val hostName: String

    val port: UInt

    val serviceContextPath: String

    val metadataQuery: String
        get() = IntrospectionQuery.INTROSPECTION_QUERY

    interface Builder {

        fun serviceName(serviceName: String): Builder

        fun hostName(hostName: String): Builder

        fun port(port: UInt): Builder

        fun serviceContextPath(serviceContextPath: String): Builder

        fun build(): GraphQLApiService

    }

}