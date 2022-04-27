package funcify.feature.materializer.service

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLExecutionSessionCoordinator {

    fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Deferred<GraphQLSingleRequestSession>
}
