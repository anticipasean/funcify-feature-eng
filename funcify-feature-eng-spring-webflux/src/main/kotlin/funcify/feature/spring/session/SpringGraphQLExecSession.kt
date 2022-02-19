package funcify.feature.spring.session

import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.session.GraphQLExecutionSession


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface SpringGraphQLExecSession : GraphQLExecutionSession {

    override val rawGraphQLRequest: RawGraphQLRequest

}