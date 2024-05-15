package funcify.feature.materializer.response.factory

import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.response.DefaultSerializedGraphQLResponse
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.response.SerializedGraphQLResponse.Builder
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.ExecutionResult
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2022-08-05
 */
internal class DefaultSerializedGraphQLResponseFactory : SerializedGraphQLResponseFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultSerializedGraphQLResponseFactory>()

        internal class DefaultBuilder(
            private var executionResult: ExecutionResult? = null,
            private var resultAsColumnarJsonObject: JsonNode? = null
        ) : Builder {

            override fun executionResult(executionResult: ExecutionResult): Builder {
                this.executionResult = executionResult
                return this
            }

            override fun resultAsColumnarJsonObject(columnarJsonObject: JsonNode): Builder {
                this.resultAsColumnarJsonObject = columnarJsonObject
                return this
            }

            override fun build(): SerializedGraphQLResponse {
                return when {
                    executionResult == null -> {
                        val message = "execution_result has not been provided"
                        logger.error("build: [ status: failed ] $message")
                        throw ServiceError.of(message)
                    }
                    else -> {
                        DefaultSerializedGraphQLResponse(
                            executionResult!!,
                            resultAsColumnarJsonObject.toOption()
                        )
                    }
                }
            }
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder()
    }
}
