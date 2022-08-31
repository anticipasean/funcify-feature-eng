package funcify.feature.materializer.session

import funcify.feature.tools.container.async.KFuture

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSessionCoordinator {

    fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): KFuture<GraphQLSingleRequestSession>
}
