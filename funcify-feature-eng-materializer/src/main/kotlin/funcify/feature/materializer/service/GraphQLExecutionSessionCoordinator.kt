package funcify.feature.materializer.service

import funcify.feature.graphql.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLExecutionSessionCoordinator {

    fun conductSingleRequestSession(session: GraphQLSingleRequestSession): Async<GraphQLSingleRequestSession>

}