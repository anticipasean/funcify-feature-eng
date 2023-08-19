package funcify.feature.materializer.input

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.json.JsonMapper
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-31
 */
internal class DefaultSingleRequestRawInputContextExtractor(private val jsonMapper: JsonMapper) :
    SingleRequestRawInputContextExtractor {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestRawInputContextExtractor>()
    }

    private val rawInputContextFactory: RawInputContextFactory =
        DefaultRawInputContextFactory(jsonMapper)

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
                Mono.fromCallable { rawInputContextFactory.builder().mapRecord(rawInput).build() }
            }
            is JsonNode -> {
                Mono.fromCallable { rawInputContextFactory.builder().json(rawInput).build() }
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
