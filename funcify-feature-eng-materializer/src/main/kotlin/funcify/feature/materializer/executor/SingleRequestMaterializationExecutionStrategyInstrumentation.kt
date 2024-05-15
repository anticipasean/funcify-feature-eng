package funcify.feature.materializer.executor

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.orElse
import arrow.core.some
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import java.util.concurrent.CompletableFuture
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-27
 */
internal class SingleRequestMaterializationExecutionStrategyInstrumentation(
    private val jsonMapper: JsonMapper
) : Instrumentation {

    companion object {
        private val logger: Logger =
            loggerFor<SingleRequestMaterializationExecutionStrategyInstrumentation>()

        private class MaterializationInstrumentationContext(private val jsonMapper: JsonMapper) :
            ExecutionStrategyInstrumentationContext {

            override fun onDispatched(result: CompletableFuture<ExecutionResult>?) {
                logger.debug("on_dispatched: [ result.is_done: {} ]", result?.isDone)
            }

            override fun onCompleted(result: ExecutionResult?, t: Throwable?) {
                val resultSpec: Option<Map<String, Any?>> =
                    Try.attemptNullable { result?.toSpecification() }
                        .orElseGet(::none)
                        .orElse { createAlternativeExecutionResultRepresentation(result) }
                logger.debug(
                    "on_completed: [ execution_result.keys: {}, execution_result.data.keys: {}, error: [ type: {}, message: {} ] ]",
                    resultSpec
                        .map(Map<String, Any?>::keys)
                        .map(Set<String>::asSequence)
                        .getOrElse(::emptySequence)
                        .joinToString(", ", "{ ", "  }"),
                    resultSpec
                        .mapNotNull { m: Map<String, Any?> -> m["data"] }
                        .filterIsInstance<Map<*, *>>()
                        .mapNotNull(Map<*, *>::keys)
                        .map(Set<Any?>::asSequence)
                        .getOrElse(::emptySequence)
                        .map { a: Any? ->
                            when (a) {
                                null -> "<NA>"
                                is String -> a
                                else -> a.toString()
                            }
                        }
                        .joinToString(", ", "{ ", " }"),
                    t?.run { this::class }?.simpleName,
                    t?.message
                )
            }

            /**
             * Necessary when a call to [ExecutionResult.toSpecification] prompts an error to bubble
             * up:
             * ```
             * java.lang.NullPointerException: Cannot invoke "graphql.language.SourceLocation.getLine()" because "location" is null
             * 	at graphql.GraphqlErrorHelper.location(GraphqlErrorHelper.java:59)
             * 	at graphql.collect.ImmutableKit.map(ImmutableKit.java:55)
             * 	at graphql.GraphqlErrorHelper.locations(GraphqlErrorHelper.java:54)
             * 	at graphql.GraphqlErrorHelper.toSpecification(GraphqlErrorHelper.java:24)
             * 	at graphql.GraphQLError.toSpecification(GraphQLError.java:61)
             * 	at graphql.collect.ImmutableKit.map(ImmutableKit.java:55)
             * 	at graphql.ExecutionResultImpl.errorsToSpec(ExecutionResultImpl.java:93)
             * 	at graphql.ExecutionResultImpl.toSpecification(ExecutionResultImpl.java:81)
             * 	```
             */
            private fun createAlternativeExecutionResultRepresentation(
                result: ExecutionResult?
            ): Option<Map<String, Any?>> {
                return when {
                    result == null -> {
                        none()
                    }
                    result.isDataPresent -> {
                        mapOf<String, Any?>(
                                "data" to result.getData(),
                                "errors" to errorsBlockRepresentation(result),
                                "extensions" to extensionsBlockRepresentation(result)
                            )
                            .some()
                    }
                    else -> {
                        mapOf<String, Any?>(
                                "errors" to errorsBlockRepresentation(result),
                                "extensions" to extensionsBlockRepresentation(result)
                            )
                            .some()
                    }
                }
            }

            private fun errorsBlockRepresentation(result: ExecutionResult): List<String>? {
                return result.errors
                    ?.asSequence()
                    ?.map { ge: GraphQLError ->
                        buildString {
                            append("[ ")
                            append("type: ")
                            append(ge.errorType)
                            append(", ")
                            append("message: ")
                            append(ge.message)
                            append(" ]")
                        }
                    }
                    ?.toList()
            }

            private fun extensionsBlockRepresentation(result: ExecutionResult): Map<String, Any?>? {
                return result.extensions
                    ?.asSequence()
                    ?.flatMap { e: Map.Entry<Any?, Any?> ->
                        when (val key: Any? = e.key) {
                            null -> emptySequence()
                            is String -> sequenceOf(key to e.value)
                            else -> sequenceOf(key.toString() to e.value)
                        }
                    }
                    ?.reducePairsToPersistentMap()
            }
        }
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters?,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult> {
        logger.debug(
            "begin_execute_operation: [ parameters.execution_context.execution_id: {}, state.type: {} ]",
            parameters?.executionContext?.executionId,
            state?.run { this::class }?.qualifiedName
        )
        when (val ec: ExecutionContext? = parameters?.executionContext) {
            null -> {
                throw ServiceError.of(
                    "parameters [ type: %s ] contains null value for [ type: %s ]",
                    InstrumentationExecuteOperationParameters::class.qualifiedName,
                    ExecutionContext::class.qualifiedName
                )
            }
            else -> {
                // updateSessionWithOperationDefinitionAndProcessedVariables(ec)
            }
        }
        return MaterializationInstrumentationContext(jsonMapper)
    }
}
