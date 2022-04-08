package funcify.feature.spring.service

import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.graphql.service.GraphQLRequestExecutor
import funcify.feature.graphql.session.GraphQLExecutionSessionFactory
import funcify.feature.tools.container.async.Async
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
open class SpringGraphQLRequestExecutor(val graphQLExecutionSessionFactory: GraphQLExecutionSessionFactory,
                                        val sessionCoordinator: GraphQLWebFluxSessionCoordinator) : GraphQLRequestExecutor {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SpringGraphQLRequestExecutor::class.java)
    }

    override fun executeSingleRequest(rawGraphQLRequest: RawGraphQLRequest): Async<SerializedGraphQLResponse> {
        return Async.succeededSingle(graphQLExecutionSessionFactory.createSessionForSingleRequest(rawGraphQLRequest))
                .flatMap { session -> sessionCoordinator.conductSingleRequestSession(session) }
                .flatMap { session ->
                    session.serializedGraphQLResponse.fold({
                                                               val message = """
                                                                   |session was not updated such that 
                                                                   |a serialized_graphql_response was added to it
                                                                   """.flattenIntoOneLine()
                                                               Async.errored(IllegalStateException(message))
                                                           },
                                                           { sr -> Async.succeededSingle(sr) })
                }
    }

}