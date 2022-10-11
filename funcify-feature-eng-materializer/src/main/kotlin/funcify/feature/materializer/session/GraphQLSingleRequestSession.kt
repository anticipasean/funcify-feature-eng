package funcify.feature.materializer.session

import arrow.core.Option
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.phase.RequestParameterMaterializationGraphPhase
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.threadlocal.ThreadLocalContextKey
import funcify.feature.schema.MetamodelGraph
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import java.util.*
import kotlinx.collections.immutable.ImmutableMap

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

    override val materializationMetamodel: MaterializationMetamodel

    val metamodelGraph: MetamodelGraph
        get() = materializationMetamodel.metamodelGraph

    val materializationSchema: GraphQLSchema
        get() = materializationMetamodel.materializationGraphQLSchema

    val rawGraphQLRequest: RawGraphQLRequest

    val document: Option<Document>

    val operationDefinition: Option<OperationDefinition>

    val processedQueryVariables: ImmutableMap<String, Any>

    val requestParameterMaterializationGraphPhase: Option<RequestParameterMaterializationGraphPhase>

    val requestDispatchMaterializationGraphPhase: Option<RequestDispatchMaterializationPhase>

    val serializedGraphQLResponse: Option<SerializedGraphQLResponse>

    fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession

    interface Builder {

        fun document(document: Document): Builder

        fun operationDefinition(operationDefinition: OperationDefinition): Builder

        fun processedQueryVariables(processedQueryVariables: Map<String, Any>): Builder

        fun requestParameterMaterializationGraphPhase(
            requestParameterMaterializationGraphPhase: RequestParameterMaterializationGraphPhase
        ): Builder

        fun requestDispatchMaterializationPhase(
            requestDispatchMaterializationPhase: RequestDispatchMaterializationPhase
        ): Builder

        fun serializedGraphQLResponse(serializedGraphQLResponse: SerializedGraphQLResponse): Builder

        fun build(): GraphQLSingleRequestSession
    }
}
