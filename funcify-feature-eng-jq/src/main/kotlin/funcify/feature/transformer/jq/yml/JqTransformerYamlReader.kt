package funcify.feature.transformer.jq.yml

import arrow.core.filterIsInstance
import arrow.core.orElse
import arrow.core.some
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerFactory
import funcify.feature.transformer.jq.definition.JqTransformerDefinition
import funcify.feature.transformer.jq.metadata.reader.JqTransformerReader
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
class JqTransformerYamlReader(
    private val jsonMapper: JsonMapper,
    private val jqTransformerFactory: JqTransformerFactory
) : JqTransformerReader<ClassPathResource> {

    companion object {
        private val logger: Logger = loggerFor<JqTransformerYamlReader>()
    }

    override fun readTransformers(resource: ClassPathResource): Mono<out List<JqTransformer>> {
        logger.info("read_transformers: [ resource.path: {} ]", resource.path)
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
                        |[ expected: path.ends_with(%s), actual: %s ]"""
                            .flatten(),
                        sequenceOf(".yaml", ".yml").joinToString(","),
                        c.path
                    )
                }
            )
            .flatMap { c: ClassPathResource ->
                try {
                    Try.success(
                        ObjectMapper(YAMLFactory.builder().build())
                            .readValue<List<JqTransformerDefinition>?>(c.inputStream)
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
                            Try.failure<List<JqTransformerDefinition>>(e)
                        }
                        else -> {
                            Try.failure<List<JqTransformerDefinition>>(
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
            .flatMap { j: List<JqTransformerDefinition> ->
                convertJQTransformerDefinitionsIntoSDLDefinitions(j)
            }
            .toMono()
    }

    private fun convertJQTransformerDefinitionsIntoSDLDefinitions(
        jqTransformerDefinitions: List<JqTransformerDefinition>
    ): Try<List<JqTransformer>> {
        return jqTransformerDefinitions
            .asSequence()
            .map { j: JqTransformerDefinition ->
                jqTransformerFactory
                    .builder()
                    .name(j.name)
                    .expression(j.expression)
                    .inputSchema(j.inputSchema)
                    .outputSchema(j.outputSchema)
                    .build()
            }
            .fold(Try.success(persistentListOf<JqTransformer>())) {
                accumulateResult: Try<PersistentList<JqTransformer>>,
                result: Try<JqTransformer> ->
                when {
                    result.isSuccess() -> {
                        accumulateResult.map { pl: PersistentList<JqTransformer> ->
                            pl.add(result.orNull()!!)
                        }
                    }
                    else -> {
                        when (accumulateResult) {
                            is Success<*> -> {
                                Try.failure(result.getFailure().orNull()!!)
                            }
                            is Failure<*> -> {
                                accumulateResult
                                    .getFailure()
                                    .filterIsInstance<ServiceError>()
                                    .orElse {
                                        ServiceError.builder()
                                            .message("jq_transformer creation error")
                                            .cause(accumulateResult.throwable)
                                            .build()
                                            .some()
                                    }
                                    .zip(
                                        result
                                            .getFailure()
                                            .filterIsInstance<ServiceError>()
                                            .orElse {
                                                ServiceError.builder()
                                                    .message("jq_transformer creation error")
                                                    .cause(result.getFailure().orNull()!!)
                                                    .build()
                                                    .some()
                                            }
                                    ) { se1: ServiceError, se2: ServiceError ->
                                        Try.failure<PersistentList<JqTransformer>>(se1 + se2)
                                    }
                                    .orNull()!!
                            }
                        }
                    }
                }
            }
    }
}
