package funcify.feature.spring.session

import arrow.core.None
import arrow.core.Option
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import java.util.UUID


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
data class DefaultGraphQLSingleRequestSession(override val rawGraphQLRequest: RawGraphQLRequest,
                                              override val serializedGraphQLResponse: Option<SerializedGraphQLResponse> = None, //TODO: Can pull session id from server_exchange in intermediate step later
                                              override val sessionIdentifier: UUID = UUID.randomUUID()) : SpringGraphQLSingleRequestSession {


}