package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import graphql.ExecutionResult
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-10-24
 */
interface MaterializationExecutionResultPostprocessingService<M : MaterializationSession> {

    fun postprocessExecutionResult(executionResult: ExecutionResult): Mono<M>

}
