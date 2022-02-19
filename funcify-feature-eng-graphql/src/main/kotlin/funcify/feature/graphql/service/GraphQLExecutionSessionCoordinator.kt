package funcify.feature.graphql.service

import funcify.feature.graphql.session.GraphQLExecutionSession
import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLExecutionSessionCoordinator {

    fun conductSession(session: GraphQLExecutionSession): Async<GraphQLExecutionSession>

}