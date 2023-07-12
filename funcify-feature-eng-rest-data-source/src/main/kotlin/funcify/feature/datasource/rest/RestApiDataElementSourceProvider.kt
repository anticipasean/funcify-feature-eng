package funcify.feature.datasource.rest

import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-10
 */
interface RestApiDataElementSourceProvider : DataElementSourceProvider<RestApiDataElementSource> {

    override val name: String

    override fun getLatestDataElementSource(): Mono<out RestApiDataElementSource>

    interface Builder {

        fun name(name: String): Builder

        fun restApiService(restApiService: RestApiService): Builder

        fun build(): Try<RestApiDataElementSourceProvider>
    }
}
