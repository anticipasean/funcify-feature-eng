package funcify.feature.graphql.session

import arrow.core.Option
import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.MaterializationSession


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : MaterializationSession {

    val rawGraphQLRequest: RawGraphQLRequest

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

}