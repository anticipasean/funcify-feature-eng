package funcify.feature.materializer.response

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.response.SerializedGraphQLResponse.Builder
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.ExecutionResult
import org.slf4j.Logger

/**
 *
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
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            message
                        )
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

        internal class DefaultSerializedGraphQLResponse(
            override val executionResult: ExecutionResult,
            override val resultAsColumnarJsonObject: Option<JsonNode> = none()
        ) : SerializedGraphQLResponse {}
    }

    override fun builder(): Builder {
        return DefaultBuilder()
    }
}
