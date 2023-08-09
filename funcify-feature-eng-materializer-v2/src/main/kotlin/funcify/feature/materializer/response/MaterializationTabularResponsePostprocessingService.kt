package funcify.feature.materializer.response

import funcify.feature.materializer.context.document.ColumnarDocumentContext
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
        columnarDocumentContext: ColumnarDocumentContext,
        session: M
    ): Mono<M>
}
