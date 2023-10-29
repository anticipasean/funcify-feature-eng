package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionResult
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
                logger.debug(
                    "on_completed: [ execution_result: {}, error: [ type: {}, message: {} ] ]",
                    result?.toSpecification()?.let { m: Map<String, Any?> ->
                        jsonMapper.fromKotlinObject(m).toJsonNode().orElseGet {
                            JsonNodeFactory.instance.nullNode()
                        }
                    },
                    t?.run { this::class }?.simpleName,
                    t?.message
                )
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
