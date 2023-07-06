package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import funcify.feature.transformer.jq.JqTransformerSourceProvider
import funcify.feature.transformer.jq.metadata.JqTransformerReader
import funcify.feature.transformer.jq.metadata.JqTransformerTypeDefinitionEnvironment
import funcify.feature.transformer.jq.metadata.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.yml.JqTransformerYamlReader
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

            override fun build(): JqTransformerSourceProvider {
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
                            throw ServiceError.of(message)
                        },
                        { p: JqTransformerSourceProvider -> p }
                    )
            }
        }

        internal class YamlResourceJqTransformerSourceProvider(
            override val name: String,
            private val yamlClassPathResource: ClassPathResource,
            private val jqTransformerReader: JqTransformerReader<ClassPathResource>,
            private val jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory
        ) : JqTransformerSourceProvider {

            override fun getLatestTransformerSource(): Mono<JqTransformerSource> {
                return jqTransformerReader
                    .readTransformers(yamlClassPathResource)
                    .flatMap { jjts: List<JqTransformer> ->
                        val transformersByName: PersistentMap<String, JqTransformer> =
                            jjts.asSequence().fold(persistentMapOf()) {
                                pm: PersistentMap<String, JqTransformer>,
                                jt: JqTransformer ->
                                pm.put(jt.name, jt)
                            }
                        if (transformersByName.size == jjts.size) {
                            Mono.just(transformersByName)
                        } else {
                            Mono.error {
                                ServiceError.of(
                                    """there exists at least one non-unique 
                                    |jq_transformer name"""
                                        .flatten()
                                )
                            }
                        }
                    }
                    .flatMap { jjtsByName: PersistentMap<String, JqTransformer> ->
                        try {
                            Mono.just(
                                DefaultJqTransformerSource(
                                    name = name,
                                    sourceTypeDefinitionRegistry =
                                        jqTransformerTypeDefinitionFactory
                                            .createTypeDefinitionRegistry(
                                                DefaultJacksonJqTypeDefinitionEnvironment(
                                                    name,
                                                    jjtsByName.values.toList()
                                                )
                                            )
                                            .getOrThrow(),
                                    transformersByName = jjtsByName,
                                )
                            )
                        } catch (t: Throwable) {
                            when (t) {
                                is ServiceError -> {
                                    Mono.error<JqTransformerSource>(t)
                                }
                                else -> {
                                    Mono.error {
                                        ServiceError.builder()
                                            .message(
                                                """jq_transformer_source 
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

        internal data class DefaultJacksonJqTypeDefinitionEnvironment(
            override val transformerSourceName: String,
            override val jqTransformers: List<JqTransformer>
        ) : JqTransformerTypeDefinitionEnvironment {}

        internal class DefaultJqTransformerSource(
            override val name: String,
            override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry =
                TypeDefinitionRegistry(),
            override val transformersByName: PersistentMap<String, JqTransformer> =
                persistentMapOf(),
        ) : JqTransformerSource {}
    }

    override fun builder(): JqTransformerSourceProvider.Builder {
        return DefaultBuilder(
            jsonMapper = jsonMapper,
            jqTransformerFactory = jqTransformerFactory,
            jqTransformerTypeDefinitionFactory = jqTransformerTypeDefinitionFactory
        )
    }
}
