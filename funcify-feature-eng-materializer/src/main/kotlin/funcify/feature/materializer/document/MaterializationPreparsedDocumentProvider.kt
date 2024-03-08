package funcify.feature.materializer.document

import funcify.feature.tools.extensions.MonoExtensions.widen
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
abstract class MaterializationPreparsedDocumentProvider : PreparsedDocumentProvider {

    abstract fun getPreparsedDocumentEntry(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry
    ): Mono<out PreparsedDocumentEntry>

    @Deprecated(
        message = "Deprecated in framework",
        replaceWith = ReplaceWith("getPreparsedDocumentEntry")
    )
    final override fun getDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): PreparsedDocumentEntry {
        return getPreparsedDocumentEntry(executionInput) { ei: ExecutionInput ->
                parseAndValidateFunction.apply(ei)
            }
            .toFuture()
            .join()
    }

    final override fun getDocumentAsync(
        executionInput: ExecutionInput,
        parseAndValidateFunction: Function<ExecutionInput, PreparsedDocumentEntry>,
    ): CompletableFuture<PreparsedDocumentEntry> {
        return getPreparsedDocumentEntry(
                executionInput = executionInput,
                parseAndValidateFunction = { ei: ExecutionInput ->
                    parseAndValidateFunction.apply(ei)
                }
            )
            .widen()
            .toFuture()
    }
}
