package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.session.field.DefaultSingleRequestFieldMaterializationSession
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLTypeUtil
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

internal class SingleRequestMaterializationDataFetcher<R>(
    private val singleRequestFieldValueMaterializer: SingleRequestFieldValueMaterializer
) : AsyncResultDataFetcher<R> {

    companion object {
        private val logger: Logger = loggerFor<SingleRequestMaterializationDataFetcher<*>>()
        private const val METHOD_TAG = "get"
    }

    override fun get(
        environment: DataFetchingEnvironment?
    ): CompletionStage<out DataFetcherResult<R>> {
        if (logger.isDebugEnabled) {
            logger.debug(
                """{}: [ 
                   |fetcher.id_hash: {}, 
                   |environment.parent_type: {}, 
                   |environment.field.name: {} 
                   |]"""
                    .flatten(),
                METHOD_TAG,
                System.identityHashCode(this),
                environment?.parentType?.run { GraphQLTypeUtil.simplePrint(this) },
                environment?.field?.name
            )
        }
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
            else -> {
                foldResultPublisherIntoDataFetcherResult<R>(
                    environment,
                    singleRequestFieldValueMaterializer.materializeValueForFieldInSession(
                        DefaultSingleRequestFieldMaterializationSession(
                            dataFetchingEnvironment = environment,
                            singleRequestSession =
                                environment.graphQlContext.get<GraphQLSingleRequestSession>(
                                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                                )
                        )
                    )
                )
            }
        }
    }

    private fun <R> createEnvironmentNullErrorFuture():
        CompletableFuture<out DataFetcherResult<R>> {
        val message = "data_fetching_environment is null"
        logger.error("$METHOD_TAG: [ status: failed ][ message: $message ]")
        return CompletableFuture.failedFuture(ServiceError.of(message))
    }

    private fun <R> createSingleRequestSessionMissingErrorFuture():
        CompletableFuture<out DataFetcherResult<R>> {
        val message =
            """data_fetching_environment.graphql_context is missing entry for key
            |[ name: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY}, 
            |type: ${GraphQLSingleRequestSession::class.qualifiedName} ]"""
                .flatten()
        logger.error("$METHOD_TAG: [ status: failed ][ message: $message ]")
        return CompletableFuture.failedFuture(ServiceError.of(message))
    }

    private fun <R> foldResultPublisherIntoDataFetcherResult(
        environment: DataFetchingEnvironment,
        resultPublisher: Mono<Any?>
    ): CompletionStage<out DataFetcherResult<R>> {
        // Unwrap completion_stage and fold materialized_value_option in
        // data_fetcher_result creation to avoid use of null within kfuture
        val resultFuture: CompletableFuture<DataFetcherResult<R>> = CompletableFuture()
        resultPublisher.subscribe(
            { resultValue: Any? ->
                resultFuture.complete(
                    foldUntypedMaterializedValueOptionIntoTypedDataFetcherResult<R>(
                        environment,
                        resultValue.toOption()
                    )
                )
            },
            { throwable: Throwable ->
                resultFuture.complete(
                    foldErrorIntoTypedDataFetcherResult<R>(environment, throwable)
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
            val message =
                """unable to convert materialized value for [ path: %s ] 
                |[ actual_kotlin_type: %s, expected_graphql_type: %s ]
                |[ class_cast_exception.message: %s ]"""
                    .flatten()
                    .format(
                        environment.executionStepInfo.path,
                        materializedValueOption.orNull()?.run { this::class.qualifiedName },
                        GraphQLTypeUtil.simplePrint(environment.fieldType),
                        cce.message
                    )
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

    private fun <R> foldErrorIntoTypedDataFetcherResult(
        environment: DataFetchingEnvironment,
        throwable: Throwable
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
                            .message(
                                "unable to determine value for [ path: %s ]"
                                    .format(environment.executionStepInfo.path)
                            )
                            .extensions(mapOf("cause" to possiblyUnnestedError.toJsonNode()))
                            .build()
                    )
                    .build()
            }
            else -> {
                val se: ServiceError =
                    ServiceError.builder()
                        .message(
                            "unhandled error during materialization of value for [ path: %s ]"
                                .format(environment.executionStepInfo.path)
                        )
                        .cause(possiblyUnnestedError)
                        .build()
                DataFetcherResult.newResult<R>()
                    .error(
                        GraphqlErrorBuilder.newError(environment)
                            .errorType(ErrorType.DataFetchingException)
                            .message(
                                "unable to determine value for [ path: %s ]"
                                    .format(environment.executionStepInfo.path)
                            )
                            .extensions(mapOf("cause" to se.toJsonNode()))
                            .build()
                    )
                    .build()
            }
        }
    }
}
