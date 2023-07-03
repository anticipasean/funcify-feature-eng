package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.transformer.jq.JacksonJqTransformerSource
import funcify.feature.transformer.jq.JacksonJqTransformerSourceProvider
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
internal class DefaultJacksonJqTransformerSourceProviderFactory :
    JacksonJqTransformerSourceProviderFactory {

    companion object {
        internal class DefaultBuilder(private var name: String? = null) :
            JacksonJqTransformerSourceProvider.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultBuilder>()
            }

            override fun name(name: String): JacksonJqTransformerSourceProvider.Builder {
                TODO("Not yet implemented")
            }

            override fun build(): JacksonJqTransformerSourceProvider {
                if (logger.isDebugEnabled) {
                    logger.debug("build: [ name: {} ]", name)
                }
                return eagerEffect<String, JacksonJqTransformerSourceProvider> {
                        ensureNotNull(name) { "name has not been provided" }
                        DefaultJacksonJqTransformerSourceProvider(name = name!!)
                    }
                    .fold(
                        { message: String ->
                            logger.error("build: [ status: failed ][ message: {} ]", message)
                            throw ServiceError.of(message)
                        },
                        { p: JacksonJqTransformerSourceProvider -> p }
                    )
            }
        }

        internal class DefaultJacksonJqTransformerSourceProvider(override val name: String) :
            JacksonJqTransformerSourceProvider {

            override fun getLatestTransformerSource(): Mono<JacksonJqTransformerSource> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): JacksonJqTransformerSourceProvider.Builder {
        return DefaultBuilder()
    }
}
