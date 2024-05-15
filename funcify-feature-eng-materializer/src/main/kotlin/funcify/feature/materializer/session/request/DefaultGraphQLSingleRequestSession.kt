package funcify.feature.materializer.session.request

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession.Builder
import graphql.execution.preparsed.PreparsedDocumentEntry

/**
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultGraphQLSingleRequestSession(
    override val materializationMetamodel: MaterializationMetamodel,
    override val rawGraphQLRequest: RawGraphQLRequest,
    override val rawInputContext: Option<RawInputContext>,
    override val preparsedDocumentEntry: Option<PreparsedDocumentEntry>,
    override val requestMaterializationGraph: Option<RequestMaterializationGraph>,
    override val dispatchedRequestMaterializationGraph:
        Option<DispatchedRequestMaterializationGraph>,
    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse>,
) : GraphQLSingleRequestSession {

    companion object {

        @JvmStatic
        fun createInitial(
            materializationMetamodel: MaterializationMetamodel,
            rawGraphQLRequest: RawGraphQLRequest
        ): GraphQLSingleRequestSession {
            return DefaultGraphQLSingleRequestSession(
                materializationMetamodel = materializationMetamodel,
                rawGraphQLRequest = rawGraphQLRequest,
                rawInputContext = none(),
                preparsedDocumentEntry = none(),
                requestMaterializationGraph = none(),
                dispatchedRequestMaterializationGraph = none(),
                serializedGraphQLResponse = none()
            )
        }

        internal data class DefaultBuilder(
            private val currentSession: DefaultGraphQLSingleRequestSession,
            private var rawGraphQLRequest: RawGraphQLRequest = currentSession.rawGraphQLRequest,
            private var rawInputContext: Option<RawInputContext> = currentSession.rawInputContext,
            private var preparsedDocumentEntry: Option<PreparsedDocumentEntry> =
                currentSession.preparsedDocumentEntry,
            private var requestMaterializationGraph: Option<RequestMaterializationGraph> =
                currentSession.requestMaterializationGraph,
            private var dispatchedRequestMaterializationGraph:
                Option<DispatchedRequestMaterializationGraph> =
                currentSession.dispatchedRequestMaterializationGraph,
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

            override fun requestMaterializationGraph(
                requestMaterializationGraph: RequestMaterializationGraph
            ): Builder {
                this.requestMaterializationGraph = requestMaterializationGraph.toOption()
                return this
            }

            override fun dispatchedRequestMaterializationGraph(
                dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
            ): Builder {
                this.dispatchedRequestMaterializationGraph =
                    dispatchedRequestMaterializationGraph.toOption()
                return this
            }

            override fun serializedGraphQLResponse(
                serializedGraphQLResponse: SerializedGraphQLResponse
            ): Builder {
                this.serializedGraphQLResponse = serializedGraphQLResponse.some()
                return this
            }

            override fun build(): GraphQLSingleRequestSession {
                return DefaultGraphQLSingleRequestSession(
                    materializationMetamodel = currentSession.materializationMetamodel,
                    rawGraphQLRequest = rawGraphQLRequest,
                    rawInputContext = rawInputContext,
                    preparsedDocumentEntry = preparsedDocumentEntry,
                    requestMaterializationGraph = requestMaterializationGraph,
                    dispatchedRequestMaterializationGraph = dispatchedRequestMaterializationGraph,
                    serializedGraphQLResponse = serializedGraphQLResponse
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
