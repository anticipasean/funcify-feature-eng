package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.ColumnarDocumentContext
import funcify.feature.materializer.document.SingleRequestMaterializationColumnarResponsePostprocessingService
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
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
    private val singleRequestMaterializationColumnarResponsePostprocessingService: SingleRequestMaterializationColumnarResponsePostprocessingService
) : SingleRequestMaterializationExecutionResultPostprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationExecutionResultPostprocessingService>()
    }

    override fun postprocessExecutionResultWithExtensions(
        executionResult: ExecutionResult
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            "postprocess_execution_result: [ execution_result: { is_data_present: {}, extensions.size: {} } ]",
            executionResult.isDataPresent,
            executionResult
                .toOption()
                .mapNotNull(ExecutionResult::getExtensions)
                .map(Map<Any?, Any?>::size)
                .getOrElse { -1 }
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
                    .flatMap { (session, columnarDocumentContext) ->
                        singleRequestMaterializationColumnarResponsePostprocessingService
                            .postprocessColumnarExecutionResult(
                                createExecutionResultWithoutExtensions(executionResult),
                                columnarDocumentContext,
                                session
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
                    .widen()
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
                "session [ type: %s ] not found within extensions for execution_result".format(
                    GraphQLSingleRequestSession::class.qualifiedName
                )
            )
        }
    }
}
