package funcify.feature.materializer.input

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import funcify.feature.error.ServiceError
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono
import kotlin.reflect.typeOf

/**
 * @author smccarron
 * @created 2023-07-31
 */
internal class DefaultSingleRequestRawInputContextExtractor :
    SingleRequestRawInputContextExtractor {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestRawInputContextExtractor>()
    }

    override fun extractRawInputContextIfProvided(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            "extract_raw_input_context_if_provided: [ session.session_id: {} ]",
            session.sessionId
        )
        return when {
            session.rawInputContext.isDefined() -> {
                Mono.just(session)
            }
            !session.rawGraphQLRequest.variables.containsKey(
                RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_KEY
            ) -> {
                Mono.just(session)
            }
            else -> {
                extractRawInputContextFromRawRequest(session.rawGraphQLRequest).map {
                    ric: RawInputContext ->
                    session.update {
                        rawGraphQLRequest(
                            session.rawGraphQLRequest.update {
                                removeVariable(RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_KEY)
                            }
                        )
                        rawInputContext(ric)
                    }
                }
            }
        }
    }

    private fun extractRawInputContextFromRawRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Mono<RawInputContext> {
        return when (
            val rawInput: Any? =
                rawGraphQLRequest.variables[RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_KEY]
        ) {
            is Map<*, *> -> {
                Try.attempt { ObjectMapper().convertValue<Map<String, String?>>(rawInput) }
                    .peekIfFailure { t: Throwable ->
                        logger.warn(
                            """build: [ failed to extract [ type: {} ] 
                            |from [ variable_name: {} ] ]
                            |[ type: {}, message: {} ]"""
                                .flatten(),
                            typeOf<Map<String, String?>>().toString(),
                            RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_KEY,
                            t::class.simpleName,
                            t.message
                        )
                    }
                    .map { m: Map<String, String?> ->
                        RawInputContextFactory.defaultFactory().builder().csvRecord(m).build()
                    }
                    .toMono()
                    .widen()
            }
            is JsonNode -> {
                Mono.fromCallable {
                    RawInputContextFactory.defaultFactory().builder().json(rawInput).build()
                }
            }
            else -> {
                Mono.error<RawInputContext> {
                    ServiceError.of(
                        "unsupported raw_input_context [ type: %s ]",
                        rawInput?.run { this::class }?.qualifiedName
                    )
                }
            }
        }
    }
}
