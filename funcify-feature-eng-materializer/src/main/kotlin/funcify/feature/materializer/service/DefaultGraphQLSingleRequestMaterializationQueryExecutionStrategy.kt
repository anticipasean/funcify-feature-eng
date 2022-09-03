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
import graphql.GraphQLError
import graphql.collect.ImmutableKit
import graphql.execution.*
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.language.SourceLocation
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-09-02
 */
internal class DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy(
    private val exceptionHandler: DataFetcherExceptionHandler =
        GraphQLSingleRequestDataFetcherExceptionHandler,
    private val singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
    private val singleRequestMaterializationPreprocessingService:
        SingleRequestMaterializationDispatchService
) : GraphQLSingleRequestMaterializationQueryExecutionStrategy(exceptionHandler) {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy>()
        private const val INTROSPECTION_FIELD_NAME_PREFIX = "__"
        private object GraphQLSingleRequestDataFetcherExceptionHandler :
            DataFetcherExceptionHandler {

            override fun handleException(
                handlerParameters: DataFetcherExceptionHandlerParameters
            ): CompletableFuture<DataFetcherExceptionHandlerResult> {
                val rootCause: Option<Throwable> =
                    handlerParameters
                        .toOption()
                        .mapNotNull { hp -> hp.exception }
                        .recurse { x ->
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

    /** More or less same as in its parent except for pre-wiring of graph creation */
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
                    """execution_context.graphql_context is missing entry for key
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
        val futures: java.util.ArrayList<CompletableFuture<FieldValueInfo>> =
            ArrayList<CompletableFuture<FieldValueInfo>>(fieldNames.size)
        val resolvedFields: MutableList<String> = ArrayList(fieldNames.size)
        for (fieldName in fieldNames) {
            val currentField: MergedField = fields.getSubField(fieldName)
            val fieldPath: ResultPath = parameters.path.segment(mkNameForPath(currentField))
            val newParameters: ExecutionStrategyParameters =
                parameters.transform { builder: ExecutionStrategyParameters.Builder ->
                    builder.field(currentField).path(fieldPath).parent(parameters)
                }
            resolvedFields.add(fieldName)
            val future: CompletableFuture<FieldValueInfo> =
                resolveFieldWithInfo(executionContext, newParameters)
            futures.add(future)
        }
        val overallResult: CompletableFuture<ExecutionResult> = CompletableFuture<ExecutionResult>()
        executionStrategyCtx.onDispatched(overallResult)

        Async.each(futures)
            .whenComplete { completeValueInfos: List<FieldValueInfo>?, throwable: Throwable? ->
                val handleResultsConsumer: BiConsumer<MutableList<ExecutionResult>?, Throwable?> =
                    handleResults(executionContext, resolvedFields, overallResult)
                if (throwable != null) {
                    handleResultsConsumer.accept(null, throwable.cause)
                    return@whenComplete
                }
                val executionResultFuture: List<CompletableFuture<ExecutionResult>> =
                    ImmutableKit.map(completeValueInfos) { obj: FieldValueInfo -> obj.fieldValue }
                executionStrategyCtx.onFieldValuesInfo(completeValueInfos)
                Async.each(executionResultFuture).whenComplete(handleResultsConsumer)
            }
            .exceptionally { ex: Throwable? ->
                executionStrategyCtx.onFieldValuesException()
                overallResult.completeExceptionally(ex)
                null
            }

        overallResult.whenComplete { result: ExecutionResult, t: Throwable? ->
            executionStrategyCtx.onCompleted(result, t)
        }
        return overallResult
    }
}
