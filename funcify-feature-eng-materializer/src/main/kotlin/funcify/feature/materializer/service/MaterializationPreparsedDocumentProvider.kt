package funcify.feature.materializer.service

import funcify.feature.materializer.threadlocal.ThreadLocalContextKey
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

    companion object {
        val EXPECTED_OUTPUT_FIELD_NAMES_KEY: ThreadLocalContextKey<List<String>> =
            ThreadLocalContextKey.of(
                MaterializationPreparsedDocumentProvider::class.qualifiedName +
                    ".EXPECTED_OUTPUT_FIELD_NAMES"
            )
    }

    @Deprecated("Deprecated in Java")
    override fun getDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): PreparsedDocumentEntry {
        return getPreparsedDocument(executionInput) { ei: ExecutionInput ->
                parseAndValidateFunction.apply(ei)
            }
            .toFuture()
            .join()
    }

    override fun getDocumentAsync(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): CompletableFuture<PreparsedDocumentEntry> {
        return getPreparsedDocument(
                executionInput = executionInput,
                parseAndValidateFunction = { ei: ExecutionInput ->
                    parseAndValidateFunction.apply(ei)
                }
            )
            .toFuture()
    }

    fun getPreparsedDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry
    ): Mono<PreparsedDocumentEntry>
}
