package funcify.feature.transformer.jq.source.provider

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.some
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.schema.sdl.SDLDefinitionsSetExtractor
import funcify.feature.tools.extensions.LoggerExtensions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import funcify.feature.transformer.jq.JqTransformerSourceProvider
import funcify.feature.transformer.jq.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.env.DefaultJacksonJqTypeDefinitionEnvironment
import funcify.feature.transformer.jq.metadata.reader.JqTransformerReader
import funcify.feature.transformer.jq.source.DefaultJqTransformerSource
import graphql.language.SDLDefinition
import graphql.language.ScalarTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
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

    override fun getLatestSource(): Mono<out JqTransformerSource> {
        val methodTag: String = "get_latest_transformer_source"
        logger.info("$methodTag: [ name: {} ]", name)
        return jqTransformerReader
            .readTransformers(yamlClassPathResource)
            .flatMap { jts: List<JqTransformer> ->
                val transformersByName: PersistentMap<String, JqTransformer> =
                    jts.asSequence().fold(persistentMapOf()) {
                        pm: PersistentMap<String, JqTransformer>,
                        jt: JqTransformer ->
                        pm.put(jt.name, jt)
                    }
                if (transformersByName.size == jts.size) {
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
            .flatMap { jtsByName: PersistentMap<String, JqTransformer> ->
                jqTransformerTypeDefinitionFactory
                    .createTypeDefinitionRegistry(
                        DefaultJacksonJqTypeDefinitionEnvironment(
                            transformerSourceName = name,
                            jqTransformers = jtsByName.values.toList()
                        )
                    )
                    .map { tdr: TypeDefinitionRegistry ->
                        DefaultJqTransformerSource(
                            name = name,
                            sourceSDLDefinitions =
                                SDLDefinitionsSetExtractor(tdr)
                                    .filter { sd: SDLDefinition<*> -> sd !is ScalarTypeDefinition }
                                    .toPersistentSet(),
                            jqTransformersByName = jtsByName,
                        )
                    }
                    .toMono()
            }
            .doOnError { t: Throwable ->
                logger.error(
                    "$methodTag: [ status: failed ][ type: {}, message/json: {} ]",
                    t::class.qualifiedName,
                    t.some()
                        .filterIsInstance<ServiceError>()
                        .mapNotNull(ServiceError::toJsonNode)
                        .mapNotNull(JsonNode::toString)
                        .getOrElse { t.message }
                )
            }
    }
}
