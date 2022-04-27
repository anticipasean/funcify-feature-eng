package funcify.feature.datasource.graphql

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.tools.container.deferred.Deferred
import graphql.introspection.IntrospectionQuery

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiService {

    val sslTlsSupported: Boolean

    val serviceName: String

    val hostName: String

    val port: UInt

    val serviceContextPath: String

    val metadataQuery: String
        get() = IntrospectionQuery.INTROSPECTION_QUERY

    fun executeSingleQuery(
        query: String,
        variables: Map<String, Any> = mapOf(),
        operationName: String? = null
    ): Deferred<JsonNode>

    interface Builder {

        fun sslTlsSupported(sslTlsSupported: Boolean): Builder

        fun serviceName(serviceName: String): Builder

        fun hostName(hostName: String): Builder

        fun port(port: UInt): Builder

        fun serviceContextPath(serviceContextPath: String): Builder

        fun build(): GraphQLApiService
    }
}
