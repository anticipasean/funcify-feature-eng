package funcify.feature.materializer.response

import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.session.MaterializationSession
import graphql.ExecutionResult
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
interface MaterializationTabularResponsePostprocessingService<M : MaterializationSession> {

    fun postprocessTabularExecutionResult(
        executionResult: ExecutionResult,
        tabularDocumentContext: TabularDocumentContext,
        session: M
    ): Mono<out M>
}
