package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.none
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNamedOutputType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import org.slf4j.Logger

internal class DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher<R>(
    private val singleRequestSessionFieldMaterializationProcessor:
        SingleRequestSessionFieldMaterializationProcessor
) : SingleRequestContextDecoratingFieldMaterializationDataFetcher<R> {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher<*>>()
    }

    override fun get(
        environment: DataFetchingEnvironment?
    ): CompletionStage<out DataFetcherResult<R>> {
        logger.debug(
            """get: [ 
               |environment.parent.type.name: ${(environment?.parentType as? GraphQLNamedOutputType)?.name}, 
               |environment.field.name: ${environment?.field?.name} 
               |]""".flatten()
        )
        return when {
            environment == null -> {
                val message = "graphql.schema.data_fetching_environment context input is null"
                logger.error("get: [ status: failed ] [ message: $message ]")
                CompletableFuture.failedFuture(
                    MaterializerException(MaterializerErrorResponse.UNEXPECTED_ERROR, message)
                )
            }
            !environment.graphQlContext.hasKey(
                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
            ) ||
                environment.graphQlContext
                    .getOrEmpty<GraphQLSingleRequestSession>(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                    .isEmpty -> {
                val message =
                    """data_fetching_environment.graphql_context is missing entry for key
                        |[ name: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY.name}, 
                        |type: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY.valueResolvableType} 
                        |]""".flatten()
                logger.error("get: [ status: failed ] [ message: $message ]")
                CompletableFuture.failedFuture(
                    MaterializerException(MaterializerErrorResponse.UNEXPECTED_ERROR, message)
                )
            }
            environment.graphQlContext.hasKey(
                SingleRequestFieldMaterializationSession
                    .SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY
            ) -> {
                unwrapCompletionStageAndFoldMaterializedValueOptionIntoDataFetcherResult(
                    environment,
                    singleRequestSessionFieldMaterializationProcessor
                        .materializeFieldValueInSession(
                            environment.graphQlContext
                                .get<SingleRequestFieldMaterializationSession>(
                                    SingleRequestFieldMaterializationSession
                                        .SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY
                                )
                                .update { dataFetchingEnvironment(environment) }
                        )
                )
            }
            else -> {
                unwrapCompletionStageAndFoldMaterializedValueOptionIntoDataFetcherResult(
                    environment,
                    singleRequestSessionFieldMaterializationProcessor
                        .materializeFieldValueInSession(
                            DefaultSingleRequestFieldMaterializationSession(
                                dataFetchingEnvironment = environment,
                                singleRequestSession =
                                    environment.graphQlContext.get<GraphQLSingleRequestSession>(
                                        GraphQLSingleRequestSession
                                            .GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                                    )
                            )
                        )
                )
            }
        }
    }

    private fun <R> unwrapCompletionStageAndFoldMaterializedValueOptionIntoDataFetcherResult(
        environment: DataFetchingEnvironment,
        sessionAndMaterializedValuePairFuture:
            Try<Pair<SingleRequestFieldMaterializationSession, KFuture<Option<Any>>>>
    ): CompletionStage<out DataFetcherResult<R>> {
        // Unwrap completion_stage and fold materialized_value_option in
        // data_fetcher_result creation to avoid use of null within kfuture
        return sessionAndMaterializedValuePairFuture.fold(
            { (session, deferredResult) ->
                environment.graphQlContext.put(
                    SingleRequestFieldMaterializationSession
                        .SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY,
                    session
                )
                deferredResult
                    .toCompletionStage()
                    .thenApply { l -> l.firstOrNull() ?: none() }
                    .thenApply { o ->
                        foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult<R>(
                            environment,
                            session,
                            o
                        )
                    }
            },
            { throwable: Throwable ->
                CompletableFuture.completedStage(
                    renderGraphQLErrorDataFetcherResultFromThrowableAndEnvironment<R>(
                        throwable,
                        environment
                    )
                )
            }
        )
    }

    private fun <R> foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult(
        environment: DataFetchingEnvironment,
        session: SingleRequestFieldMaterializationSession,
        materializedValueOption: Option<Any>
    ): DataFetcherResult<R> {
        return try {
            @Suppress("UNCHECKED_CAST") //
            val materializedValue: R = materializedValueOption.orNull() as R
            DataFetcherResult.newResult<R>().data(materializedValue).build()
        } catch (cce: ClassCastException) {
            val materializedValueType =
                materializedValueOption.mapNotNull { a -> a::class.qualifiedName }.orNull()
            val message =
                """unable to convert materialized_value: 
                    |[ actual: result.type $materializedValueType ] 
                    |[ class_cast_exception.message: ${cce.message} ]
                    |""".flatten()
            DataFetcherResult.newResult<R>()
                .error(
                    GraphqlErrorBuilder.newError(environment)
                        .errorType(ErrorType.DataFetchingException)
                        .message(message)
                        .build()
                )
                .build()
        }
    }

    private fun <R> renderGraphQLErrorDataFetcherResultFromThrowableAndEnvironment(
        throwable: Throwable,
        environment: DataFetchingEnvironment?,
    ): DataFetcherResult<R> {
        var cause: Throwable? = throwable
        while (cause?.cause != null) {
            cause = cause.cause
        }
        when (val rootCause: Throwable = cause ?: throwable) {
            is GraphQLError -> {
                return DataFetcherResult.newResult<R>().error(rootCause).build()
            }
            else -> {
                val messageWithErrorTypeInfo: String =
                    """[ type: ${rootCause::class.simpleName}, 
                       |message: ${rootCause.message}, 
                       |head_stack_trace_element: ${rootCause.possiblyNestedHeadStackTraceElement()} 
                       |]""".flatten()
                return DataFetcherResult.newResult<R>()
                    .error(
                        GraphqlErrorBuilder.newError(environment)
                            .errorType(ErrorType.DataFetchingException)
                            .message(messageWithErrorTypeInfo)
                            .build()
                    )
                    .build()
            }
        }
    }
}
