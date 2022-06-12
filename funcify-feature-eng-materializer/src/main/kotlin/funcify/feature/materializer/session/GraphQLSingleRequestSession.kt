package funcify.feature.materializer.session

import arrow.core.Option
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import graphql.schema.GraphQLSchema
import java.util.*

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : MaterializationSession {

    override val sessionId: UUID
        get() = rawGraphQLRequest.requestId

    override val materializationSchema: GraphQLSchema

    val rawGraphQLRequest: RawGraphQLRequest

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

    fun transform(updater: Builder.() -> Builder): GraphQLSingleRequestSession

    interface Builder {

        fun serializedGraphQLResponse(serializedGraphQLResponse: SerializedGraphQLResponse): Builder

        fun build(): GraphQLSingleRequestSession
    }
}
