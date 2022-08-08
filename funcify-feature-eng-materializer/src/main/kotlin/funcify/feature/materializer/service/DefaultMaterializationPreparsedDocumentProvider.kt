package funcify.feature.materializer.service

import funcify.feature.json.JsonMapper
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultMaterializationPreparsedDocumentProvider(private val jsonMapper: JsonMapper) :
    MaterializationPreparsedDocumentProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationPreparsedDocumentProvider>()
    }

    override fun getPreparsedDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry,
    ): KFuture<PreparsedDocumentEntry> {
        logger.debug(
            "get_preparsed_document: [ execution_input.execution_id: ${executionInput.executionId} ]"
        )
        return KFuture.fromAttempt(
            Try.attempt { parseAndValidateFunction.invoke(executionInput) }
                .peekIfSuccess { preparsedDocumentEntry: PreparsedDocumentEntry ->
                    logger.debug(
                        "preparsed_document_entry: [ entry.errors: ${preparsedDocumentEntry.errors.joinToString(", ", "{ ", " }", transform = {graphQLError: GraphQLError -> 
                    Try.attempt { graphQLError.toSpecification() }.flatMap { spec -> 
                        jsonMapper.fromKotlinObject(spec).toJsonString()
                    }.orNull() ?: "<NA>"
                })} ]"
                    )
                }
        )
    }
}
