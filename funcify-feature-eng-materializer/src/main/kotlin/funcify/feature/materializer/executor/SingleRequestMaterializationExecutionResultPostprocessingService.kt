package funcify.feature.materializer.executor

import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import graphql.ExecutionResult
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
interface SingleRequestMaterializationExecutionResultPostprocessingService :
    MaterializationExecutionResultPostprocessingService<GraphQLSingleRequestSession> {

    override fun postprocessExecutionResultWithExtensions(
        executionResult: ExecutionResult
    ): Mono<out GraphQLSingleRequestSession>
}
