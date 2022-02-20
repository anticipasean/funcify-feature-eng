package funcify.feature.spring.session

import arrow.core.Option
import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.graphql.session.GraphQLSingleRequestSession


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface SpringGraphQLSingleRequestSession : GraphQLSingleRequestSession {

    override val rawGraphQLRequest: RawGraphQLRequest

    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

}