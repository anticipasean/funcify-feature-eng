package funcify.feature.graphql.session

import arrow.core.Option
import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.FeatureMaterializationSession


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : FeatureMaterializationSession {

    val rawGraphQLRequest: RawGraphQLRequest

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

}