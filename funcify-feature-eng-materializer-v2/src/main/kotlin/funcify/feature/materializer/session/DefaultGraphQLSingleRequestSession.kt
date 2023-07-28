package funcify.feature.materializer.session

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.session.GraphQLSingleRequestSession.Builder
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
    override val document: Option<Document> = none(),
    override val operationDefinition: Option<OperationDefinition> = none(),
    override val processedQueryVariables: ImmutableMap<String, Any?> = persistentMapOf(),
    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse> = none(),
) : GraphQLSingleRequestSession {

    companion object {

        internal data class DefaultBuilder(
            private val currentSession: DefaultGraphQLSingleRequestSession,
            private var document: Option<Document> = currentSession.document,
            private var operationDefinition: Option<OperationDefinition> =
                currentSession.operationDefinition,
            private var processedQueryVariables: ImmutableMap<String, Any?> =
                currentSession.processedQueryVariables,
            private var serializedGraphQLResponse: Option<SerializedGraphQLResponse> =
                currentSession.serializedGraphQLResponse
        ) : Builder {

            override fun document(document: Document): Builder {
                this.document = document.toOption()
                return this
            }

            override fun operationDefinition(operationDefinition: OperationDefinition): Builder {
                this.operationDefinition = operationDefinition.toOption()
                return this
            }

            override fun processedQueryVariables(
                processedQueryVariables: Map<String, Any?>
            ): Builder {
                this.processedQueryVariables = processedQueryVariables.toPersistentMap()
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
                    document = document,
                    operationDefinition = operationDefinition,
                    processedQueryVariables = processedQueryVariables,
                    serializedGraphQLResponse = serializedGraphQLResponse
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): GraphQLSingleRequestSession {
        return transformer.invoke(DefaultBuilder(this)).build()
    }
}
