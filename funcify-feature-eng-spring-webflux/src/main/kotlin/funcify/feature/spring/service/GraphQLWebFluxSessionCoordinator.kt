package funcify.feature.spring.service

import funcify.feature.materializer.service.GraphQLExecutionSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.async.Async
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Component
class GraphQLWebFluxSessionCoordinator : GraphQLExecutionSessionCoordinator {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLWebFluxSessionCoordinator::class.java)
    }

    override fun conductSingleRequestSession(session: GraphQLSingleRequestSession): Async<GraphQLSingleRequestSession> {
        return Async.empty();
    }

}