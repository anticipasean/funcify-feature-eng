package funcify.feature.materializer.session

import arrow.core.Option
import funcify.feature.materializer.threadlocal.ThreadLocalContextKey
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema
import java.util.*

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : MaterializationSession {

    companion object {
        val GRAPHQL_SINGLE_REQUEST_SESSION_KEY: ThreadLocalContextKey<GraphQLSingleRequestSession> =
            ThreadLocalContextKey.of(GraphQLSingleRequestSession::class.qualifiedName + ".SESSION")
    }

    override val sessionId: UUID
        get() = rawGraphQLRequest.requestId

    override val materializationSchema: GraphQLSchema

    override val metamodelGraph: MetamodelGraph

    val rawGraphQLRequest: RawGraphQLRequest

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

    fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession

    interface Builder {

        fun serializedGraphQLResponse(serializedGraphQLResponse: SerializedGraphQLResponse): Builder

        fun build(): GraphQLSingleRequestSession
    }
}
