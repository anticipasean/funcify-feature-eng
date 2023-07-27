package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.service.SingleRequestMaterializationOrchestratorService
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLTypeUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher<R>(
    private val singleRequestMaterializationOrchestratorService:
        SingleRequestMaterializationOrchestratorService
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
               |environment.parent.type.name: ${environment?.parentType?.run { GraphQLTypeUtil.simplePrint(this) }}, 
               |environment.field.name: ${environment?.field?.name} 
               |]"""
                .flatten()
        )
        return when {
            environment == null -> {
                createEnvironmentNullErrorFuture()
            }
            !environment.graphQlContext.hasKey(
                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
            ) ||
                environment.graphQlContext
                    .getOrEmpty<GraphQLSingleRequestSession>(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                    .isEmpty -> {
                createSingleRequestSessionMissingErrorFuture()
            }
            environment.graphQlContext.hasKey(
                SingleRequestFieldMaterializationSession
                    .SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY
            ) -> {
                foldResultPublisherIntoDataFetcherResult<R>(
                    environment,
                    singleRequestMaterializationOrchestratorService.materializeValueInSession(
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
                foldResultPublisherIntoDataFetcherResult<R>(
                    environment,
                    singleRequestMaterializationOrchestratorService.materializeValueInSession(
                        DefaultSingleRequestFieldMaterializationSession(
                                dataFetchingEnvironment = environment,
                                singleRequestSession =
                                    environment.graphQlContext.get<GraphQLSingleRequestSession>(
                                        GraphQLSingleRequestSession
                                            .GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                                    )
                            )
                            .also { session: SingleRequestFieldMaterializationSession ->
                                environment.graphQlContext.put(
                                    SingleRequestFieldMaterializationSession
                                        .SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY,
                                    session
                                )
                            }
                    )
                )
            }
        }
    }

    private fun <R> createEnvironmentNullErrorFuture(): CompletableFuture<DataFetcherResult<R>> {
        val message = "data_fetching_environment is null"
        logger.error("get: [ status: failed ] [ message: $message ]")
        return CompletableFuture.failedFuture(ServiceError.of(message))
    }

    private fun <R> createSingleRequestSessionMissingErrorFuture():
        CompletableFuture<DataFetcherResult<R>> {
        val message =
            """data_fetching_environment.graphql_context is missing entry for key
            |[ name: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY}, 
            |type: ${GraphQLSingleRequestSession::class.qualifiedName} 
            |]"""
                .flatten()
        logger.error("get: [ status: failed ] [ message: $message ]")
        return CompletableFuture.failedFuture(ServiceError.of(message))
    }

    private fun <R> foldResultPublisherIntoDataFetcherResult(
        environment: DataFetchingEnvironment,
        resultPublisher: Mono<Any>
    ): CompletionStage<out DataFetcherResult<R>> {
        // Unwrap completion_stage and fold materialized_value_option in
        // data_fetcher_result creation to avoid use of null within kfuture
        val resultFuture: CompletableFuture<DataFetcherResult<R>> = CompletableFuture()
        resultPublisher.subscribe(
            { resultValue ->
                resultFuture.complete(
                    foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult<R>(
                        environment,
                        resultValue.toOption()
                    )
                )
            },
            { throwable ->
                resultFuture.complete(
                    renderGraphQLErrorDataFetcherResultFromThrowableAndEnvironment<R>(
                        throwable,
                        environment
                    )
                )
            },
            { ->
                // if result_future is NOT done when on_complete is called, then the
                // result_publisher is an empty publisher indicating that a NULL value should be
                // returned for the materialized_value assuming the type was scalar
                // empty collection types e.g. empty lists, sets, maps, etc. are preferred over
                // NULL values where possible
                if (!resultFuture.isDone) {
                    resultFuture.complete(
                        foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult<R>(
                            environment,
                            none()
                        )
                    )
                }
            }
        )
        return resultFuture
    }

    private fun <R> foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult(
        environment: DataFetchingEnvironment,
        materializedValueOption: Option<Any>
    ): DataFetcherResult<R> {
        return try {
            @Suppress("UNCHECKED_CAST") //
            val materializedValue: R = materializedValueOption.orNull() as R
            DataFetcherResult.newResult<R>().data(materializedValue).build()
        } catch (cce: ClassCastException) {
            val materializedValueType: String =
                materializedValueOption
                    .mapNotNull { a -> a::class.qualifiedName }
                    .getOrElse { "<NA>" }
            val message =
                """unable to convert materialized_value: 
                    |[ actual: result.type $materializedValueType ] 
                    |[ class_cast_exception.message: ${cce.message} ]
                    |"""
                    .flatten()
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
        var t: Throwable? = throwable
        while (t?.cause != null && t !is GraphQLError && t !is ServiceError) {
            t = t.cause
        }
        return when (val possiblyUnnestedError: Throwable = t ?: throwable) {
            is GraphQLError -> {
                DataFetcherResult.newResult<R>().error(possiblyUnnestedError).build()
            }
            is ServiceError -> {
                DataFetcherResult.newResult<R>()
                    .error(
                        GraphqlErrorBuilder.newError(environment)
                            .errorType(ErrorType.DataFetchingException)
                            .message(possiblyUnnestedError.message)
                            .extensions(mapOf("service_error" to possiblyUnnestedError))
                            .build()
                    )
                    .build()
            }
            else -> {
                val messageWithErrorTypeInfo: String =
                    """unhandled data_fetching_error 
                       |[ type: ${possiblyUnnestedError::class.simpleName}, 
                       |message: ${possiblyUnnestedError.message} 
                       |]"""
                        .flatten()
                DataFetcherResult.newResult<R>()
                    .error(
                        GraphqlErrorBuilder.newError(environment)
                            .errorType(ErrorType.DataFetchingException)
                            .message(messageWithErrorTypeInfo)
                            .extensions(
                                mapOf(
                                    "service_error" to
                                        ServiceError.builder()
                                            .message("unhandled data_fetching_error")
                                            .cause(possiblyUnnestedError)
                                            .build()
                                )
                            )
                            .build()
                    )
                    .build()
            }
        }
    }
}
