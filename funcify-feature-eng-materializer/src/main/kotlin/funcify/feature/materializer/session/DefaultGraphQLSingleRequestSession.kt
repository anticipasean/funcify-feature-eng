package funcify.feature.materializer.session

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.phase.RequestParameterMaterializationGraphPhase
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.schema.MetamodelGraph
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultGraphQLSingleRequestSession(
    override val materializationSchema: GraphQLSchema,
    override val metamodelGraph: MetamodelGraph,
    override val rawGraphQLRequest: RawGraphQLRequest,
    override val document: Option<Document> = none(),
    override val operationDefinition: Option<OperationDefinition> = none(),
    override val processedQueryVariables: ImmutableMap<String, Any> = persistentMapOf(),
    override val requestParameterMaterializationGraphPhase:
        Option<RequestParameterMaterializationGraphPhase> =
        none(),
    override val requestDispatchMaterializationGraphPhase:
        Option<RequestDispatchMaterializationPhase> =
        none(),
    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse> =
        none<SerializedGraphQLResponse>(),
) : GraphQLSingleRequestSession {

    companion object {

        internal data class DefaultBuilder(
            private val currentSession: DefaultGraphQLSingleRequestSession,
            private var document: Option<Document> = currentSession.document,
            private var operationDefinition: Option<OperationDefinition> =
                currentSession.operationDefinition,
            private var processedQueryVariables: ImmutableMap<String, Any> =
                currentSession.processedQueryVariables,
            private var requestParameterMaterializationGraphPhase:
                Option<RequestParameterMaterializationGraphPhase> =
                currentSession.requestParameterMaterializationGraphPhase,
            private var requestDispatchMaterializationGraphPhase:
                Option<RequestDispatchMaterializationPhase> =
                currentSession.requestDispatchMaterializationGraphPhase,
            private var serializedGraphQLResponse: Option<SerializedGraphQLResponse> =
                currentSession.serializedGraphQLResponse
        ) : GraphQLSingleRequestSession.Builder {

            override fun document(document: Document): GraphQLSingleRequestSession.Builder {
                this.document = document.toOption()
                return this
            }

            override fun operationDefinition(
                operationDefinition: OperationDefinition
            ): GraphQLSingleRequestSession.Builder {
                this.operationDefinition = operationDefinition.toOption()
                return this
            }

            override fun processedQueryVariables(
                processedQueryVariables: Map<String, Any>
            ): GraphQLSingleRequestSession.Builder {
                this.processedQueryVariables = processedQueryVariables.toPersistentMap()
                return this
            }

            override fun requestParameterMaterializationGraphPhase(
                requestParameterMaterializationGraphPhase: RequestParameterMaterializationGraphPhase
            ): GraphQLSingleRequestSession.Builder {
                this.requestParameterMaterializationGraphPhase =
                    requestParameterMaterializationGraphPhase.toOption()
                return this
            }

            override fun requestDispatchMaterializationPhase(
                requestDispatchMaterializationPhase: RequestDispatchMaterializationPhase
            ): GraphQLSingleRequestSession.Builder {
                this.requestDispatchMaterializationGraphPhase =
                    requestDispatchMaterializationPhase.toOption()
                return this
            }

            override fun serializedGraphQLResponse(
                serializedGraphQLResponse: SerializedGraphQLResponse
            ): GraphQLSingleRequestSession.Builder {
                this.serializedGraphQLResponse = serializedGraphQLResponse.some()
                return this
            }

            override fun build(): GraphQLSingleRequestSession {
                return currentSession.copy(
                    document = document,
                    operationDefinition = operationDefinition,
                    processedQueryVariables = processedQueryVariables,
                    requestParameterMaterializationGraphPhase =
                        requestParameterMaterializationGraphPhase,
                    requestDispatchMaterializationGraphPhase =
                        requestDispatchMaterializationGraphPhase,
                    serializedGraphQLResponse = serializedGraphQLResponse
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
