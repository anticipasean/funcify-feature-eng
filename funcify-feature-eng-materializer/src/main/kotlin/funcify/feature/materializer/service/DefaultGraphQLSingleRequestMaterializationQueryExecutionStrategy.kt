package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQLError
import graphql.execution.*
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.SourceLocation
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 *
 * @author smccarron
 * @created 2022-09-02
 */
internal class DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy(
    private val exceptionHandler: DataFetcherExceptionHandler =
        GraphQLSingleRequestDataFetcherExceptionHandler,
    private val globalExecutionStrategyTimeoutMilliseconds: Long,
    private val singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
    private val singleRequestMaterializationPreprocessingService:
        SingleRequestMaterializationDispatchService,
) : GraphQLSingleRequestMaterializationQueryExecutionStrategy(exceptionHandler) {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy>()
        private const val INTROSPECTION_FIELD_NAME_PREFIX = "__"
        private const val DEFAULT_GLOBAL_DATA_FETCHER_TIMEOUT_SECONDS: Long = 4
        private fun Throwable?.unnestAnyPossibleGraphQLErrorThrowable(): Option<Throwable> {
            return this.toOption().recurse { x ->
                when (x) {
                    is GraphQLError -> x.right().some()
                    else -> {
                        when (val innerCause = x.cause) {
                            null -> x.right().some()
                            else -> innerCause.left().some()
                        }
                    }
                }
            }
        }

        private object GraphQLSingleRequestDataFetcherExceptionHandler :
            DataFetcherExceptionHandler {

            override fun handleException(
                handlerParameters: DataFetcherExceptionHandlerParameters,
            ): CompletableFuture<DataFetcherExceptionHandlerResult> {
                val rootCause: Option<Throwable> =
                    handlerParameters.toOption().flatMap { hp ->
                        hp.exception.unnestAnyPossibleGraphQLErrorThrowable()
                    }
                val sourceLocation: Option<SourceLocation> =
                    handlerParameters.toOption().mapNotNull { hp -> hp.sourceLocation }
                val path: Option<ResultPath> =
                    handlerParameters.toOption().mapNotNull { hp -> hp.path }
                return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult(
                            ExceptionWhileDataFetching(
                                path.getOrElse { ResultPath.rootPath() },
                                rootCause.getOrElse {
                                    MaterializerException(
                                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                                        "error reported during data_fetching that could not be unwrapped properly"
                                    )
                                },
                                sourceLocation.getOrElse { SourceLocation.EMPTY }
                            )
                        )
                        .build()
                )
            }
        }
    }

    private val validatedGlobalExecutionStrategyTimeoutMilliseconds: Long by lazy {
        if (globalExecutionStrategyTimeoutMilliseconds < 0) {
            DEFAULT_GLOBAL_DATA_FETCHER_TIMEOUT_SECONDS
        } else {
            globalExecutionStrategyTimeoutMilliseconds
        }
    }

    private val globalExecutionStrategyTimeoutDuration: Duration by lazy {
        Duration.ofMillis(validatedGlobalExecutionStrategyTimeoutMilliseconds)
    }

    override fun execute(
        executionContext: ExecutionContext,
        parameters: ExecutionStrategyParameters,
    ): CompletableFuture<ExecutionResult> {
        logger.info("execute: [ execution_context.execution_id: {} ]", executionContext.executionId)
        when {
            executionContext.operationDefinition.name == "IntrospectionQuery" ||
                executionContext.operationDefinition
                    .toOption()
                    .mapNotNull { od: OperationDefinition -> od.selectionSet }
                    .mapNotNull { ss: SelectionSet -> ss.selections }
                    .fold(::emptyList, ::identity)
                    .asSequence()
                    .filter { s: Selection<*> ->
                        s is Field && s.name.startsWith(INTROSPECTION_FIELD_NAME_PREFIX)
                    }
                    .firstOrNull()
                    .toOption()
                    .isDefined() -> {
                return super.execute(executionContext, parameters)
            }
            !executionContext.graphQLContext.hasKey(
                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
            ) ||
                executionContext.graphQLContext
                    .getOrEmpty<GraphQLSingleRequestSession>(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                    .isEmpty -> {
                val message =
                    """execution_context.graphql_context is missing or has null entry for current session 
                        |[ name: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY.name}, 
                        |type: ${GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY.valueResolvableType} 
                        |]""".flatten()
                logger.error("execute: [ status: failed ] [ message: $message ]")
                return CompletableFuture.failedFuture(
                    MaterializerException(MaterializerErrorResponse.UNEXPECTED_ERROR, message)
                )
            }
            executionContext.graphQLContext
                .getOrEmpty<GraphQLSingleRequestSession>(
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                )
                .toOption()
                .filter { session ->
                    session.requestDispatchMaterializationGraphPhase.isDefined() &&
                        session.requestDispatchMaterializationGraphPhase.isDefined()
                }
                .isDefined() -> {
                // Do nothing
            }
            else -> {
                val graphQLSingleRequestSession: GraphQLSingleRequestSession =
                    executionContext.graphQLContext.get<GraphQLSingleRequestSession>(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                val mappedAndDispatchedSessionAttempt =
                    singleRequestMaterializationGraphService
                        .createRequestMaterializationGraphForSession(
                            session =
                                graphQLSingleRequestSession.update {
                                    document(executionContext.document)
                                        .operationDefinition(executionContext.operationDefinition)
                                        .processedQueryVariables(executionContext.variables)
                                }
                        )
                        .flatMap { session: GraphQLSingleRequestSession ->
                            singleRequestMaterializationPreprocessingService
                                .dispatchRequestsInMaterializationGraphInSession(session = session)
                        }
                if (mappedAndDispatchedSessionAttempt.isFailure()) {
                    val exceptionTypeName =
                        mappedAndDispatchedSessionAttempt
                            .getFailure()
                            .map { t -> t::class }
                            .map { tCls -> tCls.qualifiedName }
                            .getOrElse { "<NA>" }
                    val exceptionMessage: String =
                        mappedAndDispatchedSessionAttempt
                            .getFailure()
                            .mapNotNull { t -> t.message }
                            .getOrElse { "<NA>" }
                    logger.error(
                        "execute: [ status: failed ] [ type: $exceptionTypeName, message: ${exceptionMessage} ]"
                    )
                    return CompletableFuture.failedFuture(
                        mappedAndDispatchedSessionAttempt.getFailure().orNull()!!
                    )
                }
                mappedAndDispatchedSessionAttempt.consume { session: GraphQLSingleRequestSession ->
                    executionContext.graphQLContext.put(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                        session
                    )
                }
            }
        }
        val instrumentation: Instrumentation = executionContext.instrumentation
        val instrumentationParameters: InstrumentationExecutionStrategyParameters =
            InstrumentationExecutionStrategyParameters(executionContext, parameters)

        val executionStrategyCtx: ExecutionStrategyInstrumentationContext =
            instrumentation.beginExecutionStrategy(instrumentationParameters)

        val fields: MergedSelectionSet = parameters.fields
        val fieldNames: MutableSet<String> = fields.keySet()
        val fieldValueInfoMonos: MutableList<Mono<FieldValueInfo>> = mutableListOf()
        val resolvedFields: MutableList<String> = mutableListOf()
        for (fieldName in fieldNames) {
            val currentField: MergedField = fields.getSubField(fieldName)
            val fieldPath: ResultPath = parameters.path.segment(mkNameForPath(currentField))
            val newParameters: ExecutionStrategyParameters =
                parameters.transform { builder: ExecutionStrategyParameters.Builder ->
                    builder.field(currentField).path(fieldPath).parent(parameters)
                }
            resolvedFields.add(fieldName)
            val fieldValueInfoMono: Mono<FieldValueInfo> =
                Mono.fromCompletionStage { resolveFieldWithInfo(executionContext, newParameters) }
                    .subscribeOn(Schedulers.boundedElastic())
            fieldValueInfoMonos.add(fieldValueInfoMono)
        }

        val overallResult: CompletableFuture<ExecutionResult> = CompletableFuture<ExecutionResult>()
        executionStrategyCtx.onDispatched(overallResult)

        val resultsHandler: (List<ExecutionResult>?, Throwable?) -> Unit =
            createResultsHandlerForExecutionResultsForFieldNames(
                executionContext,
                resolvedFields,
                overallResult
            )
        Flux.mergeSequential(fieldValueInfoMonos)
            .collectList()
            .doOnNext { completeFieldValueInfos: List<FieldValueInfo> ->
                executionStrategyCtx.onFieldValuesInfo(completeFieldValueInfos)
            }
            .flatMapMany { completeFieldValueInfos: List<FieldValueInfo> ->
                Flux.mergeSequential(
                    completeFieldValueInfos
                        .asSequence()
                        .map { fieldValueInfo: FieldValueInfo -> fieldValueInfo.fieldValue }
                        .map { executionResultFuture ->
                            Mono.fromCompletionStage(executionResultFuture)
                                .subscribeOn(Schedulers.boundedElastic())
                        }
                        .asIterable()
                )
            }
            .collectList()
            .onErrorMap { throwable: Throwable ->
                throwable.unnestAnyPossibleGraphQLErrorThrowable().getOrElse { throwable }
            }
            .timeout(
                globalExecutionStrategyTimeoutDuration,
                globalExternalRequestTimeoutReachedExceptionCreator(
                    fieldNames,
                    globalExecutionStrategyTimeoutDuration
                )
            )
            .subscribe(
                { executionResults: MutableList<ExecutionResult> ->
                    resultsHandler(executionResults, null)
                },
                { throwable: Throwable ->
                    executionStrategyCtx.onFieldValuesException()
                    overallResult.completeExceptionally(throwable)
                    resultsHandler(null, throwable)
                }
            )

        overallResult.whenComplete { result: ExecutionResult?, t: Throwable? ->
            executionStrategyCtx.onCompleted(result, t)
        }
        return overallResult
    }

    private fun createResultsHandlerForExecutionResultsForFieldNames(
        executionContext: ExecutionContext,
        fieldNames: List<String>,
        overallResult: CompletableFuture<ExecutionResult>,
    ): (List<ExecutionResult>?, Throwable?) -> Unit {
        return { results: List<ExecutionResult>?, exception: Throwable? ->
            when {
                exception != null -> {
                    handleNonNullException(executionContext, overallResult, exception)
                }
                results == null -> {
                    exceptionAndExecutionResultsNullExceptionHandler(
                        executionContext,
                        overallResult
                    )
                }
                fieldNames.size != results.size -> {
                    if (fieldNames.size > results.size) {
                        numberOfExecutionResultsReceivedLessThanExpectedExceptionHandler(
                            fieldNames,
                            results,
                            executionContext,
                            overallResult
                        )
                    } else {
                        numberOfExecutionResultsReceivedExceedsExpectedNumberExceptionHandler(
                            executionContext,
                            overallResult,
                            fieldNames,
                            results
                        )
                    }
                }
                else -> {
                    val resolvedValuesByFieldName =
                        results
                            .toOption()
                            .fold(::emptyList, ::identity)
                            .asSequence()
                            .withIndex()
                            .fold(persistentMapOf<String, Any?>()) { pm, indexedExecResult ->
                                pm.put(
                                    fieldNames[indexedExecResult.index],
                                    // data can be null so needs to be nullable Any => Any?
                                    indexedExecResult.value.getData()
                                )
                            }
                    overallResult.complete(
                        ExecutionResultImpl(resolvedValuesByFieldName, executionContext.errors)
                    )
                }
            }
        }
    }

    private fun exceptionAndExecutionResultsNullExceptionHandler(
        executionContext: ExecutionContext,
        overallResult: CompletableFuture<ExecutionResult>,
    ) {
        handleNonNullException(
            executionContext,
            overallResult,
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """both exception and execution_results values are null; 
                   |unable to proceed with graphql_execution_strategy""".flatten()
            )
        )
    }

    private fun numberOfExecutionResultsReceivedExceedsExpectedNumberExceptionHandler(
        executionContext: ExecutionContext,
        overallResult: CompletableFuture<ExecutionResult>,
        fieldNames: List<String>,
        results: List<ExecutionResult>,
    ) {
        handleNonNullException(
            executionContext,
            overallResult,
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """number of execution_results returned exceeds 
                   |the number of results expected: 
                   |[ expected: results.size ${fieldNames.size}, 
                   |actual: results.size ${results.size} ]""".flatten()
            )
        )
    }

    private fun numberOfExecutionResultsReceivedLessThanExpectedExceptionHandler(
        fieldNames: List<String>,
        results: List<ExecutionResult>,
        executionContext: ExecutionContext,
        overallResult: CompletableFuture<ExecutionResult>,
    ) {
        val expectedFieldNamesSetAsString =
            fieldNames.asSequence().joinToString(separator = ", ", prefix = "{ ", postfix = " }")
        val receivedFieldNamesSetAsString =
            fieldNames
                .asSequence()
                .take(results.size)
                .joinToString(separator = ", ", prefix = "{ ", postfix = " }")
        handleNonNullException(
            executionContext,
            overallResult,
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """number of execution_results returned does not 
                   |match the number of results expected: 
                   |[ expected: results for field_names %s, 
                   |actual: results received for field_names: %s ]"""
                    .flatten()
                    .format(expectedFieldNamesSetAsString, receivedFieldNamesSetAsString)
            )
        )
    }

    private fun <T> globalExternalRequestTimeoutReachedExceptionCreator(
        fieldNames: Set<String>,
        globalTimeoutDuration: Duration
    ): Mono<T> {
        return Mono.fromSupplier<String> {
                fieldNames.asSequence().sorted().joinToString(", ", "{ ", " }")
            }
            .flatMap { fieldNameSetAsStr ->
                Mono.error<T>(
                    AbortExecutionException(
                        """materialization of the following fields has exceeded 
                        |the global timeout [ ${globalTimeoutDuration.toMillis()} ms ] 
                        |and has been terminated: 
                        |[ fields: $fieldNameSetAsStr ]""".flatten()
                    )
                )
            }
    }
}
