package funcify.feature.transformer.jq.source.provider

import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import funcify.feature.transformer.jq.JqTransformerSourceProvider
import funcify.feature.transformer.jq.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.env.DefaultJacksonJqTypeDefinitionEnvironment
import funcify.feature.transformer.jq.metadata.reader.JqTransformerReader
import funcify.feature.transformer.jq.source.DefaultJqTransformerSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

internal class YamlResourceJqTransformerSourceProvider(
    override val name: String,
    private val yamlClassPathResource: ClassPathResource,
    private val jqTransformerReader: JqTransformerReader<ClassPathResource>,
    private val jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory
) : JqTransformerSourceProvider {

    companion object {
        private val logger: Logger =
            LoggerExtensions.loggerFor<YamlResourceJqTransformerSourceProvider>()
    }

    override fun getLatestTransformerSource(): Mono<JqTransformerSource> {
        val methodTag: String = "get_latest_transformer_source"
        logger.info("$methodTag: [ name: {} ]", name)
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
            .doOnError { t: Throwable ->
                logger.error(
                    "$methodTag: [ status: failed ][ type: {}, message: {} ]",
                    t::class.qualifiedName,
                    t.message
                )
            }
    }
}
