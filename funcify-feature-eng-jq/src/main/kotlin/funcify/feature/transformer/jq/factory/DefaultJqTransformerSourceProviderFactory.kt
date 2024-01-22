package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JqTransformerFactory
import funcify.feature.transformer.jq.JqTransformerSourceProvider
import funcify.feature.transformer.jq.JqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.source.provider.YamlResourceJqTransformerSourceProvider
import funcify.feature.transformer.jq.yml.JqTransformerYamlReader
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource

/**
 * @author smccarron
 * @created 2023-07-02
 */
internal class DefaultJqTransformerSourceProviderFactory(
    private val jsonMapper: JsonMapper,
    private val jqTransformerFactory: JqTransformerFactory,
    private val jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory
) : JqTransformerSourceProviderFactory {

    companion object {

        internal class DefaultBuilder(
            private val jsonMapper: JsonMapper,
            private val jqTransformerFactory: JqTransformerFactory,
            private val jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory,
            private var name: String? = null,
            private var yamlClassPathResource: ClassPathResource? = null,
        ) : JqTransformerSourceProvider.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultBuilder>()
            }

            override fun name(name: String): JqTransformerSourceProvider.Builder {
                this.name = name
                return this
            }

            override fun transformerYamlFile(
                classpathResource: ClassPathResource
            ): JqTransformerSourceProvider.Builder {
                this.yamlClassPathResource = classpathResource
                return this
            }

            override fun build(): Try<JqTransformerSourceProvider> {
                if (logger.isDebugEnabled) {
                    logger.debug("build: [ name: {} ]", name)
                }
                return eagerEffect<String, JqTransformerSourceProvider> {
                        ensureNotNull(name) { "name has not been provided" }
                        ensure(yamlClassPathResource != null) {
                            """yaml_classpath_resource or other resource for 
                                |obtaining metadata has not been provided"""
                                .flatten()
                        }
                        YamlResourceJqTransformerSourceProvider(
                            name = name!!,
                            yamlClassPathResource = yamlClassPathResource!!,
                            jqTransformerReader =
                                JqTransformerYamlReader(jsonMapper, jqTransformerFactory),
                            jqTransformerTypeDefinitionFactory = jqTransformerTypeDefinitionFactory
                        )
                    }
                    .fold(
                        { message: String ->
                            logger.error("build: [ status: failed ][ message: {} ]", message)
                            Try.failure(ServiceError.of(message))
                        },
                        { p: JqTransformerSourceProvider -> Try.success(p) }
                    )
            }
        }
    }

    override fun builder(): JqTransformerSourceProvider.Builder {
        return DefaultBuilder(
            jsonMapper = jsonMapper,
            jqTransformerFactory = jqTransformerFactory,
            jqTransformerTypeDefinitionFactory = jqTransformerTypeDefinitionFactory
        )
    }
}
