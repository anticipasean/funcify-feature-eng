package funcify.feature.spring.session

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultSpringGraphQLSingleRequestSession(
    override val materializationSchema: GraphQLSchema,
    override val metamodelGraph: MetamodelGraph,
    override val rawGraphQLRequest: RawGraphQLRequest,
    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse> =
        none<SerializedGraphQLResponse>()
) : SpringGraphQLSingleRequestSession {

    companion object {

        internal data class DefaultBuilder(
            private val currentSession: DefaultSpringGraphQLSingleRequestSession,
            private var serializedGraphQLResponseOption: Option<SerializedGraphQLResponse> =
                none<SerializedGraphQLResponse>()
        ) : GraphQLSingleRequestSession.Builder {

            override fun serializedGraphQLResponse(
                serializedGraphQLResponse: SerializedGraphQLResponse
            ): GraphQLSingleRequestSession.Builder {
                this.serializedGraphQLResponseOption = serializedGraphQLResponse.some()
                return this
            }

            override fun build(): GraphQLSingleRequestSession {
                return currentSession.copy(
                    serializedGraphQLResponse = serializedGraphQLResponseOption
                )
            }
        }
    }

    override fun update(
        transformer: GraphQLSingleRequestSession.Builder.() -> GraphQLSingleRequestSession.Builder
    ): GraphQLSingleRequestSession {
        val builder: GraphQLSingleRequestSession.Builder = DefaultBuilder(this)
        return transformer.invoke(builder).build()
    }
}
