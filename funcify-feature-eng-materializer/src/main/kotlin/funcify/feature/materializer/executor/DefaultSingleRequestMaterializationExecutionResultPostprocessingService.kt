package funcify.feature.materializer.executor

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.ColumnarDocumentContext
import funcify.feature.materializer.response.SingleRequestMaterializationTabularResponsePostprocessingService
import funcify.feature.materializer.response.factory.SerializedGraphQLResponseFactory
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
internal class DefaultSingleRequestMaterializationExecutionResultPostprocessingService(
    private val serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
    private val singleRequestMaterializationTabularResponsePostprocessingService:
        SingleRequestMaterializationTabularResponsePostprocessingService
) : SingleRequestMaterializationExecutionResultPostprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationExecutionResultPostprocessingService>()
    }

    override fun postprocessExecutionResultWithExtensions(
        executionResult: ExecutionResult
    ): Mono<out GraphQLSingleRequestSession> {
        logger.info(
            "postprocess_execution_result: [ execution_result: { is_data_present: {}, extensions.size: {}, extensions.keys: {} } ]",
            executionResult.isDataPresent,
            executionResult
                .toOption()
                .mapNotNull(ExecutionResult::getExtensions)
                .map(Map<Any?, Any?>::size)
                .getOrElse { -1 },
            executionResult
                .toOption()
                .mapNotNull(ExecutionResult::getExtensions)
                .map(Map<Any?, Any?>::keys)
                .getOrElse { emptySet() }
                .joinToString(", ")
        )
        return when {
            executionResult.extensions == null -> {
                createNoExtensionsOnExecutionResultErrorPublisher()
            }
            ColumnarDocumentContext.COLUMNAR_DOCUMENT_CONTEXT_KEY in executionResult.extensions -> {
                executionResult.extensions
                    .getOrNone(GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY)
                    .filterIsInstance<GraphQLSingleRequestSession>()
                    .successIfDefined(sessionNotFoundWithinDeclaredExtensionsExceptionSupplier())
                    .zip(
                        executionResult.extensions
                            .getOrNone(ColumnarDocumentContext.COLUMNAR_DOCUMENT_CONTEXT_KEY)
                            .filterIsInstance<ColumnarDocumentContext>()
                    )
                    .toMono()
                    .flatMap { (s: GraphQLSingleRequestSession, cdc: ColumnarDocumentContext) ->
                        singleRequestMaterializationTabularResponsePostprocessingService
                            .postprocessTabularExecutionResult(
                                createExecutionResultWithoutExtensions(executionResult),
                                cdc,
                                s
                            )
                    }
            }
            else -> {
                executionResult.extensions
                    .getOrNone(GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY)
                    .filterIsInstance<GraphQLSingleRequestSession>()
                    .map { session: GraphQLSingleRequestSession ->
                        session.update {
                            serializedGraphQLResponse(
                                serializedGraphQLResponseFactory
                                    .builder()
                                    .executionResult(
                                        createExecutionResultWithoutExtensions(executionResult)
                                    )
                                    .build()
                            )
                        }
                    }
                    .successIfDefined(sessionNotFoundWithinDeclaredExtensionsExceptionSupplier())
                    .toMono()
            }
        }
    }

    /**
     * Remove extensions provided upstream since these are not expected to be part of the published
     * API and serializable
     */
    private fun createExecutionResultWithoutExtensions(
        executionResult: ExecutionResult
    ): ExecutionResult {
        return if (executionResult.isDataPresent) {
            ExecutionResultImpl.newExecutionResult()
                .data(executionResult.getData())
                .errors(executionResult.errors)
                .build()
        } else {
            ExecutionResultImpl.newExecutionResult().errors(executionResult.errors).build()
        }
    }

    private fun <T> createNoExtensionsOnExecutionResultErrorPublisher(): Mono<T> {
        return Mono.error {
            ServiceError.of(
                "no extensions were passed into execution_result in query_execution_strategy"
            )
        }
    }

    private fun sessionNotFoundWithinDeclaredExtensionsExceptionSupplier(): () -> ServiceError {
        return { ->
            ServiceError.of(
                "session [ type: %s ] not found within extensions for execution_result"
                    .format(GraphQLSingleRequestSession::class.qualifiedName)
            )
        }
    }
}
