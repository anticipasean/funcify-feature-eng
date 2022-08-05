package funcify.feature.materializer.response

import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
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

        internal class DefaultBuilder(private var executionResult: ExecutionResult? = null) :
            SerializedGraphQLResponse.Builder {

            override fun executionResult(
                executionResult: ExecutionResult
            ): SerializedGraphQLResponse.Builder {
                this.executionResult = executionResult
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
                        DefaultSerializedGraphQLResponse(executionResult!!)
                    }
                }
            }
        }

        internal class DefaultSerializedGraphQLResponse(
            override val executionResult: ExecutionResult
        ) : SerializedGraphQLResponse {}
    }

    override fun builder(): SerializedGraphQLResponse.Builder {
        return DefaultBuilder()
    }
}
