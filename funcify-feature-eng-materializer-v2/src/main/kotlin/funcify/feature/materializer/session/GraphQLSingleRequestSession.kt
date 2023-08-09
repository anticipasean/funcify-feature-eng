package funcify.feature.materializer.session

import arrow.core.Option
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.FeatureEngineeringModel
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import java.util.*
import kotlinx.collections.immutable.ImmutableMap

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

    val processedQueryVariables: ImmutableMap<String, Any?>

    val requestMaterializationGraph: Option<RequestMaterializationGraph>

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

    fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession

    interface Builder {

        fun rawGraphQLRequest(rawGraphQLRequest: RawGraphQLRequest): Builder

        fun rawInputContext(rawInputContext: RawInputContext): Builder

        fun preparsedDocumentEntry(preparsedDocumentEntry: PreparsedDocumentEntry): Builder

        fun processedQueryVariables(processedQueryVariables: Map<String, Any?>): Builder

        fun requestMaterializationGraph(
            requestMaterializationGraph: RequestMaterializationGraph
        ): Builder

        fun serializedGraphQLResponse(serializedGraphQLResponse: SerializedGraphQLResponse): Builder

        fun build(): GraphQLSingleRequestSession
    }
}
