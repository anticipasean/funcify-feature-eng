package funcify.feature.spring.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.GraphQLExecutionSessionFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
open class SpringGraphQLExecutionSessionFactory : GraphQLExecutionSessionFactory {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLExecutionSessionFactory>()
    }

    override fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): GraphQLSingleRequestSession {
        logger.info(
            """create_session_for_single_request: 
                |[ raw_graphql_request.request_id: ${rawGraphQLRequest.requestId} ]
                |""".flattenIntoOneLine()
        )
        return DefaultGraphQLSingleRequestSession(rawGraphQLRequest = rawGraphQLRequest)
    }
}
