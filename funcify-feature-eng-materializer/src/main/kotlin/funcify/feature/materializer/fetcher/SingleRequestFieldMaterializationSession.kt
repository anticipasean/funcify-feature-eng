package funcify.feature.materializer.fetcher

import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.materializer.threadlocal.ThreadLocalContextKey
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.GraphQLContext
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import java.util.*

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface SingleRequestFieldMaterializationSession : MaterializationSession {

    companion object {
        val SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY:
            ThreadLocalContextKey<SingleRequestFieldMaterializationSession> =
            ThreadLocalContextKey.of(
                SingleRequestFieldMaterializationSession::class.qualifiedName + ".SESSION"
            )
    }

    val dataFetchingEnvironment: DataFetchingEnvironment

    val singleRequestSession: GraphQLSingleRequestSession

    val requestMaterializationGraph:
        PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    override val sessionId: UUID
        get() = singleRequestSession.sessionId

    override val materializationSchema: GraphQLSchema
        get() = singleRequestSession.materializationSchema

    override val metamodelGraph: MetamodelGraph
        get() = singleRequestSession.metamodelGraph

    val graphQLContext: GraphQLContext
        get() = dataFetchingEnvironment.graphQlContext

    val graphQLFieldDefinition: GraphQLFieldDefinition
        get() = dataFetchingEnvironment.fieldDefinition

    val field: Field
        get() = dataFetchingEnvironment.field

    val fieldOutputType: GraphQLOutputType
        get() = dataFetchingEnvironment.fieldType

    fun update(transformer: Builder.() -> Builder): SingleRequestFieldMaterializationSession

    interface Builder {

        fun dataFetchingEnvironment(dataFetchingEnvironment: DataFetchingEnvironment): Builder

        fun requestMaterializationGraph(
            requestMaterializationGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
        ): Builder

        fun build(): SingleRequestFieldMaterializationSession
    }
}
