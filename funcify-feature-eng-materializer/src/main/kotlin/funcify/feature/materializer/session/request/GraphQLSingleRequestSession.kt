package funcify.feature.materializer.session.request

import arrow.core.Option
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.schema.FeatureEngineeringModel
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.schema.GraphQLSchema
import java.util.*

/**
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSession : MaterializationSession {

    companion object {
        val GRAPHQL_SINGLE_REQUEST_SESSION_KEY: String =
            GraphQLSingleRequestSession::class.qualifiedName + ".SESSION"
    }

    override val sessionId: UUID
        get() = rawGraphQLRequest.requestId

    override val materializationMetamodel: MaterializationMetamodel

    val featureEngineeringModel: FeatureEngineeringModel
        get() = materializationMetamodel.featureEngineeringModel

    val materializationSchema: GraphQLSchema
        get() = materializationMetamodel.materializationGraphQLSchema

    val rawGraphQLRequest: RawGraphQLRequest

    val rawInputContext: Option<RawInputContext>

    val preparsedDocumentEntry: Option<PreparsedDocumentEntry>

    val requestMaterializationGraph: Option<RequestMaterializationGraph>

    val dispatchedRequestMaterializationGraph: Option<DispatchedRequestMaterializationGraph>

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

    fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession

    interface Builder {

        fun rawGraphQLRequest(rawGraphQLRequest: RawGraphQLRequest): Builder

        fun rawInputContext(rawInputContext: RawInputContext): Builder

        fun preparsedDocumentEntry(preparsedDocumentEntry: PreparsedDocumentEntry): Builder

        fun requestMaterializationGraph(
            requestMaterializationGraph: RequestMaterializationGraph
        ): Builder

        fun dispatchedRequestMaterializationGraph(
            dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
        ): Builder

        fun serializedGraphQLResponse(serializedGraphQLResponse: SerializedGraphQLResponse): Builder

        fun build(): GraphQLSingleRequestSession
    }
}
