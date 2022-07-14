package funcify.feature.materializer.fetcher

import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.async.KFuture.Companion.flatMapFailure
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNamedOutputType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import org.slf4j.Logger

internal class DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher(
    private val singleRequestSessionFieldMaterializationProcessor:
        SingleRequestSessionFieldMaterializationProcessor
) : SingleRequestContextDecoratingFieldMaterializationDataFetcher<Any?> {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher>()
    }

    override fun get(
        environment: DataFetchingEnvironment?
    ): CompletionStage<out DataFetcherResult<Any?>> {
        logger.debug(
            """get: [ 
               |environment.parent.type.name: ${(environment?.parentType as? GraphQLNamedOutputType)?.name}, 
               |environment.field.name: ${environment?.field?.name} 
               |]""".flattenIntoOneLine()
        )
        if (environment == null) {
            val message = "graphql.schema.data_fetching_environment context input is null"
            logger.error("get: [ status: failed ] [ message: $message ]")
            return CompletableFuture.failedFuture(
                MaterializerException(MaterializerErrorResponse.UNEXPECTED_ERROR, message)
            )
        } else {
            return singleRequestSessionFieldMaterializationProcessor
                .materializeFieldValueInContext(
                    DefaultSingleRequestFieldMaterializationContext(
                        dataFetchingEnvironment = environment
                    )
                )
                .map { materializedValue ->
                    DataFetcherResult.newResult<Any?>().data(materializedValue).build()
                }
                .flatMapFailure { throwable: Throwable ->
                    KFuture.completed(
                        renderGraphQLErrorDataFetcherResultFromThrowableAndEnvironment(
                            throwable,
                            environment
                        )
                    )
                }
                .fold { completionStage, _ -> completionStage }
        }
    }

    private fun renderGraphQLErrorDataFetcherResultFromThrowableAndEnvironment(
        throwable: Throwable,
        environment: DataFetchingEnvironment?,
    ): DataFetcherResult<Any> {
        when (throwable) {
            is GraphQLError -> {
                return DataFetcherResult.newResult<Any?>().error(throwable).build()
            }
            else -> {
                val messageWithErrorTypeInfo: String =
                    """[ type: ${throwable::class.simpleName}, 
                       |message: ${throwable.message} ]""".flattenIntoOneLine()
                return DataFetcherResult.newResult<Any?>()
                    .error(
                        GraphqlErrorBuilder.newError(environment)
                            .errorType(ErrorType.DataFetchingException)
                            .message(messageWithErrorTypeInfo)
                            .build()
                    )
                    .build()
            }
        }
    }
}
