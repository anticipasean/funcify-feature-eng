package funcify.feature.materializer.context.publishing

import arrow.core.continuations.eagerEffect
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableJsonValuePublisher
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.materializer.context.publishing.TrackableValuePublishingContext.Builder
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2022-12-02
 */
internal class DefaultTrackableValuePublishingContextFactory :
    TrackableValuePublishingContextFactory {

    companion object {

        internal class DefaultTrackableValuePublishingContextBuilder(
            private var session: GraphQLSingleRequestSession? = null,
            private var dispatchedRequest: TrackableSingleJsonValueDispatch? = null,
            private var publisher: TrackableJsonValuePublisher? = null,
            private var calculatedValue: TrackableValue.CalculatedValue<JsonNode>? = null,
            private var lastUpdatedInstantsByPath: PersistentMap.Builder<SchematicPath, Instant> =
                persistentMapOf<SchematicPath, Instant>().builder(),
            private var entityIdentifierValuesByPath:
                PersistentMap.Builder<SchematicPath, JsonNode> =
                persistentMapOf<SchematicPath, JsonNode>().builder(),
        ) : Builder {

            override fun graphQLSingleRequestSession(
                session: GraphQLSingleRequestSession
            ): Builder {
                this.session = session
                return this
            }

            override fun dispatchedRequest(
                dispatchedRequest: TrackableSingleJsonValueDispatch
            ): Builder {
                this.dispatchedRequest = dispatchedRequest
                return this
            }

            override fun trackableJsonValuePublisher(
                publisher: TrackableJsonValuePublisher
            ): Builder {
                this.publisher = publisher
                return this
            }

            override fun calculatedValue(
                calculatedValue: TrackableValue.CalculatedValue<JsonNode>
            ): Builder {
                this.calculatedValue = calculatedValue
                return this
            }

            override fun putLastUpdatedInstantForPath(
                path: SchematicPath,
                lastUpdatedInstant: Instant
            ): Builder {
                this.lastUpdatedInstantsByPath[path] = lastUpdatedInstant
                return this
            }

            override fun putAllLastUpdatedInstantsForPaths(
                lastUpdatedInstantsByPath: Map<SchematicPath, Instant>
            ): Builder {
                this.lastUpdatedInstantsByPath.putAll(lastUpdatedInstantsByPath)
                return this
            }

            override fun removeLastUpdatedInstantForPath(path: SchematicPath): Builder {
                this.lastUpdatedInstantsByPath.remove(path)
                return this
            }

            override fun clearLastUpdatedInstantsByPath(): Builder {
                this.lastUpdatedInstantsByPath.clear()
                return this
            }

            override fun putEntityIdentifierValueForPath(
                path: SchematicPath,
                entityIdentifierValue: JsonNode
            ): Builder {
                this.entityIdentifierValuesByPath[path] = entityIdentifierValue
                return this
            }

            override fun putAllEntityIdentifierValuesForPaths(
                entityIdentifiersByPath: Map<SchematicPath, JsonNode>
            ): Builder {
                this.entityIdentifierValuesByPath.putAll(entityIdentifiersByPath)
                return this
            }

            override fun removeEntityIdentifierValueForPath(path: SchematicPath): Builder {
                this.entityIdentifierValuesByPath.remove(path)
                return this
            }

            override fun clearEntityIdentifierValuesForPaths(): Builder {
                this.entityIdentifierValuesByPath.clear()
                return this
            }

            override fun build(): TrackableValuePublishingContext {
                return eagerEffect<String, TrackableValuePublishingContext> {
                        ensure(session != null) { "session has not been specified" }
                        ensure(dispatchedRequest != null) {
                            "dispatched_request has not been specified"
                        }
                        ensure(publisher != null) { "publisher has not been specified" }
                        ensure(calculatedValue != null) {
                            "calculated_value has not been specified"
                        }
                        DefaultTrackableValuePublishingContext(
                            session = session!!,
                            dispatchedRequest = dispatchedRequest!!,
                            publisher = publisher!!,
                            calculatedValue = calculatedValue!!,
                            lastUpdatedInstantsByPath = lastUpdatedInstantsByPath.build(),
                            entityIdentifierValuesByPath = entityIdentifierValuesByPath.build()
                        )
                    }
                    .fold(
                        { message: String ->
                            throw MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                """cannot create ${TrackableValuePublishingContext::class.simpleName}: 
                                   |[ message: ${message} 
                                   |""".flatten()
                            )
                        },
                        { context -> context }
                    )
            }
        }

        internal data class DefaultTrackableValuePublishingContext(
            override val session: GraphQLSingleRequestSession,
            override val dispatchedRequest: TrackableSingleJsonValueDispatch,
            override val publisher: TrackableJsonValuePublisher,
            override val calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
            override val lastUpdatedInstantsByPath: ImmutableMap<SchematicPath, Instant>,
            override val entityIdentifierValuesByPath: ImmutableMap<SchematicPath, JsonNode>,
        ) : TrackableValuePublishingContext {

            override fun update(
                transformer: Builder.() -> Builder
            ): TrackableValuePublishingContext {
                return transformer(
                        DefaultTrackableValuePublishingContextBuilder(
                            session = session,
                            dispatchedRequest = dispatchedRequest,
                            publisher = publisher,
                            calculatedValue = calculatedValue,
                            lastUpdatedInstantsByPath =
                                lastUpdatedInstantsByPath.toPersistentMap().builder(),
                            entityIdentifierValuesByPath =
                                entityIdentifierValuesByPath.toPersistentMap().builder()
                        )
                    )
                    .build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultTrackableValuePublishingContextBuilder()
    }
}
