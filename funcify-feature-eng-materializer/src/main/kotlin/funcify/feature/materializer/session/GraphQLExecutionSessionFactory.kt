package funcify.feature.materializer.session

import funcify.feature.materializer.request.RawGraphQLRequest


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface GraphQLExecutionSessionFactory {

    fun createSessionForSingleRequest(rawGraphQLRequest: RawGraphQLRequest): GraphQLSingleRequestSession

}