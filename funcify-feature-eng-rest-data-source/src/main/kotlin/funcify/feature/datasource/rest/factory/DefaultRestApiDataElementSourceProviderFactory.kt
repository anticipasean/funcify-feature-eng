package funcify.feature.datasource.rest.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.datasource.rest.RestApiDataElementSource
import funcify.feature.datasource.rest.RestApiDataElementSourceProvider
import funcify.feature.datasource.rest.RestApiDataElementSourceProviderFactory
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Mono

internal class DefaultRestApiDataElementSourceProviderFactory : RestApiDataElementSourceProviderFactory {

    companion object {

        internal class DefaultBuilder(
            private var name: String? = null,
            private var restApiService: RestApiService? = null
        ) : RestApiDataElementSourceProvider.Builder {

            override fun name(name: String): RestApiDataElementSourceProvider.Builder {
                this.name = name
                return this
            }

            override fun restApiService(
                restApiService: RestApiService
            ): RestApiDataElementSourceProvider.Builder {
                this.restApiService = restApiService
                return this
            }

            override fun build(): Try<RestApiDataElementSourceProvider> {
                return eagerEffect<String, RestApiDataElementSourceProvider> {
                        ensureNotNull(name) { "name has not been provided" }
                        ensureNotNull(restApiService) { "rest_api_service has not been provided" }
                        DefaultRestApiDataElementSourceProvider(name!!, restApiService!!)
                    }
                    .fold(
                        { message: String -> Try.failure(ServiceError.of(message)) },
                        { p: RestApiDataElementSourceProvider -> Try.success(p) }
                    )
            }
        }

        internal class DefaultRestApiDataElementSourceProvider(
            override val name: String,
            private val restApiService: RestApiService
        ) : RestApiDataElementSourceProvider {

            override fun getLatestSource(): Mono<out RestApiDataElementSource> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): RestApiDataElementSourceProvider.Builder {
        return DefaultBuilder()
    }
}
