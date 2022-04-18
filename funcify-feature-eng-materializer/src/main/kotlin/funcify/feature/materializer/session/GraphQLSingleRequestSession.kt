package funcify.feature.materializer.session

import arrow.core.Option
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : MaterializationSession {

    val rawGraphQLRequest: RawGraphQLRequest

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>
}
