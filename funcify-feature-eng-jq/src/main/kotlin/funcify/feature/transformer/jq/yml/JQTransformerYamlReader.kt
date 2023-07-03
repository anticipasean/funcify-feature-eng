package funcify.feature.transformer.jq.yml

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.metadata.JQTransformerReader
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
class JQTransformerYamlReader(private val jsonMapper: JsonMapper) :
    JQTransformerReader<ClassPathResource> {

    companion object {
        private val logger: Logger = loggerFor<JQTransformerYamlReader>()
    }

    override fun readMetadata(resource: ClassPathResource): Mono<TypeDefinitionRegistry> {
        logger.info("read_metadata: [ resource.path: {} ]", resource.path)
        return Try.success(resource)
            .filter(ClassPathResource::exists) { c: ClassPathResource ->
                ServiceError.of("resource.path does not exist [ path: %s ]", c.path)
            }
            .filter(
                { c: ClassPathResource ->
                    sequenceOf(".yml", ".yaml").any { extension: String ->
                        c.path.endsWith(extension)
                    }
                },
                { c: ClassPathResource ->
                    ServiceError.of(
                        """resource.path likely is not a yaml file: 
                        |[ expected: %s, actual: %s ]"""
                            .flatten(),
                        sequenceOf(".yaml", ".yml").joinToString(","),
                        c.path
                    )
                }
            )
            .flatMap { c: ClassPathResource ->
                try {
                    Try.success(
                        jsonMapper.jacksonObjectMapper
                            .copyWith(YAMLFactory.builder().build())
                            .readValue<JQTransformerDefinitions?>(c.inputStream)
                            ?: throw ServiceError.of(
                                """null value interpreted for yaml resource: 
                                |[ resource.path: %s ]"""
                                    .flatten(),
                                c.path
                            )
                    )
                } catch (e: Exception) {
                    when (e) {
                        is ServiceError -> {
                            Try.failure<JQTransformerDefinitions>(e)
                        }
                        else -> {
                            Try.failure<JQTransformerDefinitions>(
                                ServiceError.builder()
                                    .message(
                                        """JSON processing error occurred when 
                                        |reading yaml resource for jq transformers: 
                                        |[ resource.path: %s ]"""
                                            .flatten(),
                                        c.path
                                    )
                                    .cause(e)
                                    .build()
                                                                 )
                        }
                    }
                }
            }
            .flatMap { j: JQTransformerDefinitions -> convertJQTransformerDefinitionsIntoSDLDefinitions(j) }
            .toMono()
            .widen()
    }

    private fun convertJQTransformerDefinitionsIntoSDLDefinitions(
        jqTransformerDefinitions: JQTransformerDefinitions
    ): Try<TypeDefinitionRegistry> {
        jqTransformerDefinitions.transformerDefinitions.asSequence().map { j: JQTransformerDefinition ->

        }
    }
}
