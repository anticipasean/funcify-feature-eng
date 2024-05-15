package funcify.feature.materializer.response

import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import graphql.ExecutionResult
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
interface SingleRequestMaterializationTabularResponsePostprocessingService :
    MaterializationTabularResponsePostprocessingService<GraphQLSingleRequestSession> {

    override fun postprocessTabularExecutionResult(
        executionResult: ExecutionResult,
        tabularDocumentContext: TabularDocumentContext,
        session: GraphQLSingleRequestSession,
    ): Mono<out GraphQLSingleRequestSession>
}
