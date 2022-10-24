package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-10-24
 */
interface MaterializationColumnarDocumentPreprocessingService<M : MaterializationSession> {

    fun preprocessColumnarDocumentForExecutionInput(
        executionInput: ExecutionInput,
        session: M
    ): Mono<PreparsedDocumentEntry>
}
