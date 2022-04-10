package funcify.feature.spring.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.GraphQLExecutionSessionFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
open class SpringGraphQLExecutionSessionFactory : GraphQLExecutionSessionFactory {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SpringGraphQLExecutionSessionFactory::class.java)
    }

    override fun createSessionForSingleRequest(rawGraphQLRequest: RawGraphQLRequest): GraphQLSingleRequestSession {
        return DefaultGraphQLSingleRequestSession(rawGraphQLRequest = rawGraphQLRequest)
    }

}