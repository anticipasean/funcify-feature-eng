package funcify.feature.materializer.document

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
interface SingleRequestMaterializationColumnarDocumentPreprocessingService :
    MaterializationColumnarDocumentPreprocessingService<GraphQLSingleRequestSession> {

    override fun preprocessColumnarDocumentForExecutionInput(
        executionInput: ExecutionInput,
        session: GraphQLSingleRequestSession
    ): Mono<PreparsedDocumentEntry>
}
