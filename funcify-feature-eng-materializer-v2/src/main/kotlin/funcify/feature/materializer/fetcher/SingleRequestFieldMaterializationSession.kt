package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.schema.path.result.GQLResultPath
import graphql.GraphQLContext
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeUtil
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

    val gqlResultPath: GQLResultPath

    val graphQLContext: GraphQLContext
        get() = dataFetchingEnvironment.graphQlContext

    val graphQLFieldDefinition: GraphQLFieldDefinition
        get() = dataFetchingEnvironment.fieldDefinition

    val field: Field
        get() = dataFetchingEnvironment.field

    val fieldOutputType: GraphQLOutputType
        get() = dataFetchingEnvironment.fieldType

    val parentImplementingType: Option<GraphQLImplementingType>

    val fieldCoordinates: Option<FieldCoordinates>

    fun update(transformer: Builder.() -> Builder): SingleRequestFieldMaterializationSession

    interface Builder {

        fun singleRequestSession(singleRequestSession: GraphQLSingleRequestSession): Builder

        fun dataFetchingEnvironment(dataFetchingEnvironment: DataFetchingEnvironment): Builder

        fun build(): SingleRequestFieldMaterializationSession
    }
}
