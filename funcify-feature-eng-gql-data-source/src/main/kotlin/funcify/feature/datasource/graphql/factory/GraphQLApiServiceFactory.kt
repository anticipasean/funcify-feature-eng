package funcify.feature.datasource.graphql.factory

import com.fasterxml.jackson.annotation.JsonProperty
import funcify.feature.datasource.graphql.GraphQLApiService


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal object GraphQLApiServiceFactory {

    private const val UNSET_SERVICE_NAME: String = ""
    private const val UNSET_HOST_NAME: String = ""
    private const val UNSET_PORT: UInt = 0u
    private const val UNSET_CONTEXT_PATH: String = ""

    internal class DefaultGraphQLApiServiceBuilder(private var serviceName: String = UNSET_SERVICE_NAME,
                                                   private var hostName: String = UNSET_HOST_NAME,
                                                   private var port: UInt = UNSET_PORT,
                                                   private var serviceContextPath: String = UNSET_CONTEXT_PATH) : GraphQLApiService.Builder {

        override fun serviceName(serviceName: String): GraphQLApiService.Builder {
            this.serviceName = serviceName
            return this
        }

        override fun hostName(hostName: String): GraphQLApiService.Builder {
            this.hostName = hostName
            return this
        }

        override fun port(port: UInt): GraphQLApiService.Builder {
            this.port = port
            return this
        }

        override fun serviceContextPath(serviceContextPath: String): GraphQLApiService.Builder {
            this.serviceContextPath = serviceContextPath
            return this
        }

        override fun build(): GraphQLApiService {
            when {
                serviceName == UNSET_SERVICE_NAME -> throw  IllegalStateException("service_name has not been set")
                hostName == UNSET_HOST_NAME -> throw IllegalStateException("host_name has not be set")
                port == UNSET_PORT -> throw IllegalStateException("port has not been set")
                serviceContextPath == UNSET_CONTEXT_PATH -> throw IllegalStateException("context_path has not be set")
                else -> {
                    return DefaultGraphQLApiService(serviceName = serviceName,
                                                    hostName = hostName,
                                                    port = port,
                                                    serviceContextPath = serviceContextPath)
                }
            }
        }

    }

    internal data class DefaultGraphQLApiService(@JsonProperty("service_name")
                                                 override val serviceName: String,
                                                 @JsonProperty("host_name")
                                                 override val hostName: String,
                                                 @JsonProperty("port")
                                                 override val port: UInt,
                                                 @JsonProperty("service_context_path")
                                                 override val serviceContextPath: String) : GraphQLApiService {

    }


}