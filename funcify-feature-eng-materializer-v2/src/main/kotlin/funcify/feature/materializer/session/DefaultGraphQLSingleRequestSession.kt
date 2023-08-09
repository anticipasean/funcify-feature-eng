package funcify.feature.materializer.session

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.session.GraphQLSingleRequestSession.Builder
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultGraphQLSingleRequestSession(
    override val materializationMetamodel: MaterializationMetamodel,
    override val rawGraphQLRequest: RawGraphQLRequest,
    override val rawInputContext: Option<RawInputContext> = none(),
    override val preparsedDocumentEntry: Option<PreparsedDocumentEntry> = none(),
    override val processedQueryVariables: ImmutableMap<String, Any?> = persistentMapOf(),
    override val requestMaterializationGraph: Option<RequestMaterializationGraph> = none(),
    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse> = none(),
) : GraphQLSingleRequestSession {

    companion object {

        internal data class DefaultBuilder(
            private val currentSession: DefaultGraphQLSingleRequestSession,
            private var rawGraphQLRequest: RawGraphQLRequest = currentSession.rawGraphQLRequest,
            private var rawInputContext: Option<RawInputContext> = currentSession.rawInputContext,
            private var preparsedDocumentEntry: Option<PreparsedDocumentEntry> =
                currentSession.preparsedDocumentEntry,
            private var processedQueryVariables: ImmutableMap<String, Any?> =
                currentSession.processedQueryVariables,
            private var requestMaterializationGraph: Option<RequestMaterializationGraph> =
                currentSession.requestMaterializationGraph,
            private var serializedGraphQLResponse: Option<SerializedGraphQLResponse> =
                currentSession.serializedGraphQLResponse
        ) : Builder {

            override fun rawGraphQLRequest(rawGraphQLRequest: RawGraphQLRequest): Builder {
                this.rawGraphQLRequest = rawGraphQLRequest
                return this
            }

            override fun rawInputContext(rawInputContext: RawInputContext): Builder {
                this.rawInputContext = rawInputContext.toOption()
                return this
            }

            override fun preparsedDocumentEntry(
                preparsedDocumentEntry: PreparsedDocumentEntry
            ): Builder {
                this.preparsedDocumentEntry = preparsedDocumentEntry.toOption()
                return this
            }

            override fun processedQueryVariables(
                processedQueryVariables: Map<String, Any?>
            ): Builder {
                this.processedQueryVariables = processedQueryVariables.toPersistentMap()
                return this
            }

            override fun requestMaterializationGraph(
                requestMaterializationGraph: RequestMaterializationGraph
            ): Builder {
                this.requestMaterializationGraph = requestMaterializationGraph.toOption()
                return this
            }

            override fun serializedGraphQLResponse(
                serializedGraphQLResponse: SerializedGraphQLResponse
            ): Builder {
                this.serializedGraphQLResponse = serializedGraphQLResponse.some()
                return this
            }

            override fun build(): GraphQLSingleRequestSession {
                return currentSession.copy(
                    rawGraphQLRequest = rawGraphQLRequest,
                    rawInputContext = rawInputContext,
                    preparsedDocumentEntry = preparsedDocumentEntry,
                    processedQueryVariables = processedQueryVariables,
                    requestMaterializationGraph = requestMaterializationGraph,
                    serializedGraphQLResponse = serializedGraphQLResponse
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
