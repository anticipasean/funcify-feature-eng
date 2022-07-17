package funcify.feature.datasource.rest.swagger

import arrow.core.Option
import funcify.feature.datasource.rest.RestApiService
import kotlinx.collections.immutable.ImmutableCollection

/**
 * Immutable container type for housing associations between swagger schema endpoint setups and rest
 * api services that implement them
 * @author smccarron
 * @created 2022-07-09
 */
interface SwaggerSchemaEndpointRegistry {

    companion object {

        fun newRegistry(): SwaggerSchemaEndpointRegistry {
            return DefaultSwaggerSchemaEndpointRegistry()
        }

    }

    fun addOrUpdateSwaggerSchemaEndpointForRestApiService(
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint,
        restApiService: RestApiService
    ): SwaggerSchemaEndpointRegistry

    fun removeSwaggerSchemaEndpoint(
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint
    ): SwaggerSchemaEndpointRegistry

    fun getSwaggerSchemaEndpointForRestApiService(
        restApiService: RestApiService
    ): Option<SwaggerSchemaEndpoint>

    fun getRestApiServicesWithSwaggerSchemaEndpoints(): ImmutableCollection<RestApiService>
}
