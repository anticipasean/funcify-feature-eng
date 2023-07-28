package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ExecutionContext
import graphql.execution.ResultPath
import graphql.extensions.ExtensionsBuilder
import graphql.language.SourceLocation
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2022-09-02
 */
internal class DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy(
    private val exceptionHandler: DataFetcherExceptionHandler =
        GraphQLSingleRequestDataFetcherExceptionHandler,
) : GraphQLSingleRequestMaterializationQueryExecutionStrategy(exceptionHandler) {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy>()
        private const val INTROSPECTION_FIELD_NAME_PREFIX = "__"
        private const val DEFAULT_GLOBAL_DATA_FETCHER_TIMEOUT_SECONDS: Long = 20
        private fun Throwable?.unnestAnyPossibleGraphQLErrorThrowable(): Option<Throwable> {
            return this.toOption().recurse { x ->
                when (x) {
                    is GraphQLError -> x.right().some()
                    else -> {
                        when (val innerCause: Throwable? = x.cause) {
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
                                    ServiceError.of(
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

    /**
     * Overrides the default handleResults method in order to pass the extensions from
     * execution_results to overall result and graphql_context entries from execution_context to
     * overall result extensions
     */
    override fun handleResults(
        executionContext: ExecutionContext,
        fieldNames: List<String>,
        overallResult: CompletableFuture<ExecutionResult?>,
    ): BiConsumer<List<ExecutionResult>?, Throwable?> {
        return BiConsumer<List<ExecutionResult>?, Throwable?> {
            results: List<ExecutionResult>?,
            exception: Throwable? ->
            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception)
            } else {
                val resolvedValuesByField: MutableMap<String, Any?> = LinkedHashMap(fieldNames.size)
                var extensions: MutableMap<Any?, Any?>? = null
                var index = 0
                for (executionResult: ExecutionResult in results ?: emptyList()) {
                    resolvedValuesByField[fieldNames[index++]] = executionResult.getData()
                    if (executionResult.extensions != null) {
                        extensions =
                            extensions?.apply { putAll(executionResult.extensions) }
                                ?: LinkedHashMap(executionResult.extensions)
                    }
                }
                overallResult.complete(
                    ExecutionResult.newExecutionResult()
                        .data(resolvedValuesByField)
                        .addErrors(executionContext.errors)
                        .extensions(
                            executionContext.graphQLContext
                                .stream()
                                .filter { (k: Any?, _: Any?) ->
                                    k is Class<*> &&
                                        ExtensionsBuilder::class.java.isAssignableFrom(k)
                                }
                                .toPersistentMap()
                                .run {
                                    // assumes the latest extension entries from the
                                    // execution_results
                                    // should take precedence i.e. replace any existing entries in
                                    // the
                                    // graphql_context
                                    if (extensions == null) {
                                        this
                                    } else {
                                        this.putAll(extensions)
                                    }
                                }
                        )
                        .build()
                )
            }
        }
    }
}
