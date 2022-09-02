package funcify.feature.materializer.service

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleRequestMaterializationDispatchService :
    MaterializationRequestDispatchService<GraphQLSingleRequestSession> {

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Try<GraphQLSingleRequestSession>
}
