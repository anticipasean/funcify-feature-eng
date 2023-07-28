package funcify.feature.materializer.fetcher

import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.MaterializationSession
import graphql.GraphQLContext
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import java.util.*

/**
 * @author smccarron
 * @created 2022-07-14
 */
interface SingleRequestFieldMaterializationSession : MaterializationSession {

    companion object {
        val SINGLE_REQUEST_FIELD_MATERIALIZATION_SESSION_KEY: String =
            SingleRequestFieldMaterializationSession::class.qualifiedName + ".SESSION"
    }

    val dataFetchingEnvironment: DataFetchingEnvironment

    val singleRequestSession: GraphQLSingleRequestSession

    override val sessionId: UUID
        get() = singleRequestSession.sessionId

    override val materializationMetamodel: MaterializationMetamodel
        get() = singleRequestSession.materializationMetamodel

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

        fun singleRequestSession(singleRequestSession: GraphQLSingleRequestSession): Builder

        fun dataFetchingEnvironment(dataFetchingEnvironment: DataFetchingEnvironment): Builder

        fun build(): SingleRequestFieldMaterializationSession
    }
}
