package funcify.feature.materializer.response

import funcify.feature.materializer.context.document.ColumnarDocumentContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.ExecutionResult
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-10-24
 */
interface SingleRequestMaterializationTabularResponsePostprocessingService :
    MaterializationTabularResponsePostprocessingService<GraphQLSingleRequestSession> {

    override fun postprocessTabularExecutionResult(
        executionResult: ExecutionResult,
        columnarDocumentContext: ColumnarDocumentContext,
        session: GraphQLSingleRequestSession,
                                                  ): Mono<GraphQLSingleRequestSession>
}
