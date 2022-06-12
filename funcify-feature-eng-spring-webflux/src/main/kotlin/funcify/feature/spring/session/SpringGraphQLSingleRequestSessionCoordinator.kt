package funcify.feature.spring.session

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Component
class SpringGraphQLSingleRequestSessionCoordinator : GraphQLSingleRequestSessionCoordinator {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestSessionCoordinator>()
    }

    override fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Deferred<GraphQLSingleRequestSession> {
        logger.info(
            """conduct_single_request_session: [ 
                |session.session_identifier: ${session.sessionId} ]
                |""".flattenIntoOneLine()
        )
        return Deferred.empty()
    }
}
