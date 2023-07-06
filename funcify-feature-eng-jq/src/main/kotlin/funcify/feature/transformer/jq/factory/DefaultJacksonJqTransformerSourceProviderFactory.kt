package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JacksonJqTransformer
import funcify.feature.transformer.jq.JacksonJqTransformerSource
import funcify.feature.transformer.jq.JacksonJqTransformerSourceProvider
import funcify.feature.transformer.jq.metadata.JQTransformerReader
import funcify.feature.transformer.jq.metadata.TransformerTypeDefinitionRegistryCreator
import funcify.feature.transformer.jq.yml.JQTransformerYamlReader
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
internal class DefaultJacksonJqTransformerSourceProviderFactory(
    private val jsonMapper: JsonMapper,
    private val jacksonJqTransformerFactory: JacksonJqTransformerFactory
) : JacksonJqTransformerSourceProviderFactory {

    companion object {
        internal class DefaultBuilder(
            private val jsonMapper: JsonMapper,
            private val jacksonJqTransformerFactory: JacksonJqTransformerFactory,
            private var name: String? = null,
            private var yamlClassPathResource: ClassPathResource? = null,
        ) : JacksonJqTransformerSourceProvider.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultBuilder>()
            }

            override fun name(name: String): JacksonJqTransformerSourceProvider.Builder {
                this.name = name
                return this
            }

            override fun transformerYamlFile(
                classpathResource: ClassPathResource
            ): JacksonJqTransformerSourceProvider.Builder {
                this.yamlClassPathResource = classpathResource
                return this
            }

            override fun build(): JacksonJqTransformerSourceProvider {
                if (logger.isDebugEnabled) {
                    logger.debug("build: [ name: {} ]", name)
                }
                return eagerEffect<String, JacksonJqTransformerSourceProvider> {
                        ensureNotNull(name) { "name has not been provided" }
                        ensure(yamlClassPathResource != null) {
                            """yaml_classpath_resource or other resource for 
                                |obtaining metadata has not been provided"""
                                .flatten()
                        }
                        YamlResourceJacksonJqTransformerSourceProvider(
                            name = name!!,
                            yamlClassPathResource = yamlClassPathResource!!,
                            jqTransformerReader =
                                JQTransformerYamlReader(jsonMapper, jacksonJqTransformerFactory)
                        )
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

        internal class YamlResourceJacksonJqTransformerSourceProvider(
            override val name: String,
            private val yamlClassPathResource: ClassPathResource,
            private val jqTransformerReader: JQTransformerReader<ClassPathResource>
        ) : JacksonJqTransformerSourceProvider {

            override fun getLatestTransformerSource(): Mono<JacksonJqTransformerSource> {
                return jqTransformerReader
                    .readTransformers(yamlClassPathResource)
                    .flatMap { jjts: List<JacksonJqTransformer> ->
                        val transformersByName: PersistentMap<String, JacksonJqTransformer> =
                            jjts.asSequence().fold(persistentMapOf()) {
                                pm: PersistentMap<String, JacksonJqTransformer>,
                                jt: JacksonJqTransformer ->
                                pm.put(jt.name, jt)
                            }
                        if (transformersByName.size == jjts.size) {
                            Mono.just(transformersByName)
                        } else {
                            Mono.error {
                                ServiceError.of(
                                    """there exists at least one non-unique 
                                    |jackson_jq_transformer name"""
                                        .flatten()
                                )
                            }
                        }
                    }
                    .flatMap { jjtsByName: PersistentMap<String, JacksonJqTransformer> ->
                        try {
                            Mono.just(
                                DefaultJacksonJqTransformerSource(
                                    name = name,
                                    sourceTypeDefinitionRegistry =
                                        TransformerTypeDefinitionRegistryCreator.invoke(
                                            jjtsByName.values
                                        ),
                                    transformersByName = jjtsByName,
                                )
                            )
                        } catch (t: Throwable) {
                            when (t) {
                                is ServiceError -> {
                                    Mono.error<JacksonJqTransformerSource>(t)
                                }
                                else -> {
                                    Mono.error {
                                        ServiceError.builder()
                                            .message(
                                                """jackson_jq_transformer_source 
                                                |type_definition_registry creation error"""
                                                    .flatten()
                                            )
                                            .cause(t)
                                            .build()
                                    }
                                }
                            }
                        }
                    }
            }
        }

        internal class DefaultJacksonJqTransformerSource(
            override val name: String,
            override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry =
                TypeDefinitionRegistry(),
            override val transformersByName: PersistentMap<String, JacksonJqTransformer> =
                persistentMapOf(),
        ) : JacksonJqTransformerSource {}
    }

    override fun builder(): JacksonJqTransformerSourceProvider.Builder {
        return DefaultBuilder(
            jsonMapper = jsonMapper,
            jacksonJqTransformerFactory = jacksonJqTransformerFactory
        )
    }
}
