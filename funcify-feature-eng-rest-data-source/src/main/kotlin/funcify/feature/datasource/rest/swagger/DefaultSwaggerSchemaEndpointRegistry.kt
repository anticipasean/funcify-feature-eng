package funcify.feature.datasource.rest.swagger

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.rest.RestApiService
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Assumes [SwaggerSchemaEndpoint]s and [RestApiService]s are both not "hashable" or that merely
 * their names should be used to assess their identities rather than their other components. So,
 * this implementation tracks each endpoint and service by their names exclusively
 * @author smccarron
 * @created 2022-07-09
 */
internal data class DefaultSwaggerSchemaEndpointRegistry(
    private val swaggerSchemaSupportedRestApiServicesByName: PersistentMap<String, RestApiService> =
        persistentMapOf(),
    private val swaggerSchemaEndpointsByName: PersistentMap<String, SwaggerSchemaEndpoint> =
        persistentMapOf(),
    private val restApiServiceNameToSwaggerSchemaEndpointNameMap: PersistentMap<String, String> =
        persistentMapOf()
) : SwaggerSchemaEndpointRegistry {

    override fun addOrUpdateSwaggerSchemaEndpointForRestApiService(
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint,
        restApiService: RestApiService,
    ): SwaggerSchemaEndpointRegistry {
        return copy(
            swaggerSchemaSupportedRestApiServicesByName =
                swaggerSchemaSupportedRestApiServicesByName.put(
                    restApiService.serviceName,
                    restApiService
                ),
            swaggerSchemaEndpointsByName =
                swaggerSchemaEndpointsByName.put(swaggerSchemaEndpoint.name, swaggerSchemaEndpoint),
            restApiServiceNameToSwaggerSchemaEndpointNameMap =
                restApiServiceNameToSwaggerSchemaEndpointNameMap.put(
                    restApiService.serviceName,
                    swaggerSchemaEndpoint.name
                )
        )
    }

    override fun removeSwaggerSchemaEndpoint(
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint
    ): SwaggerSchemaEndpointRegistry {
        return if (swaggerSchemaEndpoint.name in swaggerSchemaEndpointsByName) {
            restApiServiceNameToSwaggerSchemaEndpointNameMap
                .asSequence()
                .filter { (_, swaggerEndptName) -> swaggerEndptName == swaggerSchemaEndpoint.name }
                .map { (restSrvName, _) -> restSrvName }
                .fold(
                    copy(
                        swaggerSchemaEndpointsByName =
                            swaggerSchemaEndpointsByName.remove(swaggerSchemaEndpoint.name)
                    )
                ) { registry: DefaultSwaggerSchemaEndpointRegistry, restSrvName: String ->
                    registry.copy(
                        swaggerSchemaSupportedRestApiServicesByName =
                            registry.swaggerSchemaSupportedRestApiServicesByName.remove(
                                restSrvName
                            ),
                        restApiServiceNameToSwaggerSchemaEndpointNameMap =
                            registry.restApiServiceNameToSwaggerSchemaEndpointNameMap.remove(
                                restSrvName
                            )
                    )
                }
        } else {
            this
        }
    }

    override fun getSwaggerSchemaEndpointForRestApiService(
        restApiService: RestApiService
    ): Option<SwaggerSchemaEndpoint> {
        return restApiService
            .serviceName
            .toOption()
            .flatMap { restApiServiceName ->
                restApiServiceNameToSwaggerSchemaEndpointNameMap[restApiServiceName].toOption()
            }
            .flatMap { swaggerEndpointName ->
                swaggerSchemaEndpointsByName[swaggerEndpointName].toOption()
            }
    }

    override fun getRestApiServicesWithSwaggerSchemaEndpoints():
        ImmutableCollection<RestApiService> {
        return swaggerSchemaSupportedRestApiServicesByName.values
    }
}
