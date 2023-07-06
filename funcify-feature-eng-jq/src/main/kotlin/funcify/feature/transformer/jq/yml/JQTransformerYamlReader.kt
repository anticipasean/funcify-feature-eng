package funcify.feature.transformer.jq.yml

import arrow.core.filterIsInstance
import arrow.core.orElse
import arrow.core.some
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JacksonJqTransformer
import funcify.feature.transformer.jq.factory.JacksonJqTransformerFactory
import funcify.feature.transformer.jq.metadata.JQTransformerReader
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
class JQTransformerYamlReader(
    private val jsonMapper: JsonMapper,
    private val jacksonJqTransformerFactory: JacksonJqTransformerFactory
) : JQTransformerReader<ClassPathResource> {

    companion object {
        private val logger: Logger = loggerFor<JQTransformerYamlReader>()
    }

    override fun readTransformers(resource: ClassPathResource): Mono<List<JacksonJqTransformer>> {
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
            .flatMap { j: JQTransformerDefinitions ->
                convertJQTransformerDefinitionsIntoSDLDefinitions(j)
            }
            .toMono()
            .widen()
    }

    private fun convertJQTransformerDefinitionsIntoSDLDefinitions(
        jqTransformerDefinitions: JQTransformerDefinitions
    ): Try<List<JacksonJqTransformer>> {
        return jqTransformerDefinitions.transformerDefinitions
            .asSequence()
            .map { j: JQTransformerDefinition ->
                jacksonJqTransformerFactory
                    .builder()
                    .name(j.name)
                    .expression(j.expression)
                    .inputSchema(j.inputSchema)
                    .outputSchema(j.outputSchema)
                    .build()
            }
            .fold(Try.success(persistentListOf<JacksonJqTransformer>())) {
                accumulateResult: Try<PersistentList<JacksonJqTransformer>>,
                result: Try<JacksonJqTransformer> ->
                when {
                    result.isSuccess() -> {
                        accumulateResult.map { pl: PersistentList<JacksonJqTransformer> ->
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
                                            .message("jackson_jq_transformer creation error")
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
                                                    .message(
                                                        "jackson_jq_transformer creation error"
                                                    )
                                                    .cause(result.getFailure().orNull()!!)
                                                    .build()
                                                    .some()
                                            }
                                    ) { se1: ServiceError, se2: ServiceError ->
                                        Try.failure<PersistentList<JacksonJqTransformer>>(se1 + se2)
                                    }
                                    .orNull()!!
                            }
                        }
                    }
                }
            }
    }
}
