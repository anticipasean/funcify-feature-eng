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
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQLError
import graphql.execution.AbortExecutionException
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.FieldValueInfo
import graphql.execution.MergedField
import graphql.execution.ResultPath
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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
        logger.info(
            "execute: [ execution_context.execution_id: {}, execution_strategy_parameters.fields: {} ]",
            executionContext.executionId,
            parameters.fields.keySet().asSequence().sorted().joinToString(", ", "{ ", " }")
        )
        when {
            isIntrospectionQuery(executionContext) -> {
                // Use standard execute implementation
                return super.execute(executionContext, parameters)
            }
            graphQLSingleRequestSessionMissingInExecutionContext(executionContext) -> {
                return createMissingGraphQLSingleRequestSessionErrorExecutionResult()
            }
            !graphQLSingleRequestSessionHasDefinedMaterializationPhases(executionContext) -> {
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
                    return createMaterializationPhaseFailureExecutionResult(
                        mappedAndDispatchedSessionAttempt
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
        return resolveFieldsIntoSingleExecutionResult(executionContext, parameters)
    }

    private fun isIntrospectionQuery(executionContext: ExecutionContext): Boolean {
        return executionContext.operationDefinition.name == "IntrospectionQuery" ||
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
                .isDefined()
    }

    private fun graphQLSingleRequestSessionMissingInExecutionContext(
        executionContext: ExecutionContext
    ): Boolean {
        return !executionContext.graphQLContext.hasKey(
            GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
        ) ||
            executionContext.graphQLContext
                .getOrEmpty<GraphQLSingleRequestSession>(
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                )
                .isEmpty
    }

    private fun createMissingGraphQLSingleRequestSessionErrorExecutionResult():
        CompletableFuture<ExecutionResult> {
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

    private fun graphQLSingleRequestSessionHasDefinedMaterializationPhases(
        executionContext: ExecutionContext
    ): Boolean {
        return executionContext.graphQLContext
            .getOrEmpty<GraphQLSingleRequestSession>(
                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
            )
            .toOption()
            .filter { session ->
                session.requestDispatchMaterializationGraphPhase.isDefined() &&
                    session.requestDispatchMaterializationGraphPhase.isDefined()
            }
            .isDefined()
    }

    private fun createMaterializationPhaseFailureExecutionResult(
        mappedAndDispatchedSessionAttempt: Try<GraphQLSingleRequestSession>
    ): CompletableFuture<ExecutionResult> {
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

    private fun resolveFieldsIntoSingleExecutionResult(
        executionContext: ExecutionContext,
        executionStrategyParameters: ExecutionStrategyParameters
    ): CompletableFuture<ExecutionResult> {
        val overallResult: CompletableFuture<ExecutionResult> = CompletableFuture<ExecutionResult>()
        val instrumentation: Instrumentation = executionContext.instrumentation
        val instrumentationParameters: InstrumentationExecutionStrategyParameters =
            InstrumentationExecutionStrategyParameters(
                executionContext,
                executionStrategyParameters
            )
        val executionStrategyInstrumentationContext: ExecutionStrategyInstrumentationContext =
            instrumentation.beginExecutionStrategy(instrumentationParameters)
        executionStrategyInstrumentationContext.onDispatched(overallResult)
        Flux.fromIterable(executionStrategyParameters.fields.subFields.entries)
            .map { (fieldName: String, mergedField: MergedField) ->
                fieldName to
                    executionStrategyParameters.transform { builder ->
                        builder
                            .field(mergedField)
                            .path(
                                executionStrategyParameters.path.segment(mkNameForPath(mergedField))
                            )
                            .parent(executionStrategyParameters)
                    }
            }
            .flatMapSequential { (fieldName: String, parameters: ExecutionStrategyParameters) ->
                Mono.fromCompletionStage { resolveFieldWithInfo(executionContext, parameters) }
                    .map { fieldValueInfo: FieldValueInfo -> fieldName to fieldValueInfo }
                    .doOnNext { (fieldName: String, fieldValueInfo: FieldValueInfo) ->
                        logger.info(
                            """resolve_fields_into_single_execution_result: 
                            |[ field_value_info_step: 
                            |[ field_name: {}, complete_value_type: {} ] 
                            |]""".flatten(),
                            fieldName,
                            fieldValueInfo.completeValueType
                        )
                        executionStrategyInstrumentationContext.onFieldValuesInfo(
                            listOf(fieldValueInfo)
                        )
                    }
                    .onErrorMap { t: Throwable ->
                        t.unnestAnyPossibleGraphQLErrorThrowable().getOrElse { t }
                    }
                    .doOnError { t: Throwable ->
                        logger.info(
                            """resolve_fields_into_single_execution_result: 
                                |[ status: failed ]
                                |[ field_value_info_step: 
                                |[ field_name: {}, error: 
                                |[ type: {}, message: {} ] 
                                |] 
                                |]""".flatten(),
                            fieldName,
                            t::class.simpleName,
                            t.message
                        )
                        executionStrategyInstrumentationContext.onFieldValuesException()
                    }
            }
            .flatMapSequential { (fieldName: String, fieldValueInfo: FieldValueInfo) ->
                Mono.fromCompletionStage { fieldValueInfo.fieldValue }
                    .map { executionResult: ExecutionResult -> fieldName to executionResult }
                    .doOnNext { (fieldName, executionResult) ->
                        logger.info(
                            """resolve_fields_into_single_execution_result: 
                            |[ execution_result_step: 
                            |[ field_name: {}, execution_result: {} ] 
                            |]""".flatten(),
                            fieldName,
                            executionResult.getData()
                        )
                    }
                    .timeout(
                        globalExecutionStrategyTimeoutDuration,
                        globalExternalRequestTimeoutReachedExceptionCreator(
                            executionStrategyParameters.fields.keySet(),
                            globalExecutionStrategyTimeoutDuration
                        )
                    )
                    .onErrorMap { t: Throwable ->
                        t.unnestAnyPossibleGraphQLErrorThrowable().getOrElse { t }
                    }
                    .onErrorResume { t: Throwable ->
                        logger.error(
                            """resolve_fields_into_single_execution_result: 
                            |[ status: failed ] 
                            |[ execution_result_step: 
                            |[ field_name: {}, error: 
                            |[ type: {}, message: {} ] 
                            |] 
                            |]""".flatten(),
                            fieldName,
                            t::class.simpleName,
                            t.message
                        )
                        val future: CompletableFuture<ExecutionResult> = CompletableFuture()
                        handleNonNullException(executionContext, future, t)
                        Mono.fromCompletionStage(future).map { executionResult: ExecutionResult ->
                            fieldName to executionResult
                        }
                    }
            }
            .reduceWith({ persistentMapOf<String, Any?>() to persistentListOf<GraphQLError>() }) {
                (dataByFieldName, errors),
                (fieldName, execResult) ->
                when {
                    execResult.isDataPresent -> {
                        dataByFieldName.put(fieldName, execResult.getData()) to
                            errors.addAll(execResult.errors)
                    }
                    else -> {
                        dataByFieldName to errors.addAll(execResult.errors)
                    }
                }
            }
            .map { (dataByFieldName, errors) -> ExecutionResultImpl(dataByFieldName, errors) }
            .subscribe(
                { executionResult: ExecutionResult ->
                    overallResult.complete(executionResult)
                    executionStrategyInstrumentationContext.onCompleted(executionResult, null)
                },
                { t: Throwable ->
                    overallResult.completeExceptionally(t)
                    executionStrategyInstrumentationContext.onCompleted(null, t)
                }
            )
        return overallResult
    }

    private fun <T> globalExternalRequestTimeoutReachedExceptionCreator(
        fieldNames: Set<String>,
        globalTimeoutDuration: Duration,
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
