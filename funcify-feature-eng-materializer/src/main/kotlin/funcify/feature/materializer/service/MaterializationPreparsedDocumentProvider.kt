package funcify.feature.materializer.service

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
interface MaterializationPreparsedDocumentProvider : PreparsedDocumentProvider {

    @Deprecated("Deprecated in Java")
    override fun getDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): PreparsedDocumentEntry {
        return getPreparsedDocumentEntry(executionInput) { ei: ExecutionInput ->
                parseAndValidateFunction.apply(ei)
            }
            .toFuture()
            .join()
    }

    override fun getDocumentAsync(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): CompletableFuture<PreparsedDocumentEntry> {
        return getPreparsedDocumentEntry(
                executionInput = executionInput,
                parseAndValidateFunction = { ei: ExecutionInput ->
                    parseAndValidateFunction.apply(ei)
                }
            )
            .toFuture()
    }

    fun getPreparsedDocumentEntry(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry
    ): Mono<PreparsedDocumentEntry>
}
