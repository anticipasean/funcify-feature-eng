package funcify.feature.graphql.session

import funcify.feature.graphql.request.RawGraphQLRequest


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface GraphQLExecutionSessionFactory {

    fun createSessionForSingleRequest(rawGraphQLRequest: RawGraphQLRequest): GraphQLSingleRequestSession

}