package funcify.feature.materializer.service

import funcify.feature.tools.container.async.KFuture
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import java.util.concurrent.CompletableFuture
import java.util.function.Function

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
        return getPreparsedDocument(executionInput) { ei: ExecutionInput ->
                parseAndValidateFunction.apply(ei)
            }
            .getOrElseThrow()
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
            .fold { stage, _ -> stage.toCompletableFuture().thenApply { pde -> pde } }
    }

    fun getPreparsedDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry
    ): KFuture<PreparsedDocumentEntry>
}
