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
import funcify.feature.tools.extensions.StringExtensions.flatten

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
            private var calculatedValue: TrackableValue.CalculatedValue<JsonNode>? = null
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
                            calculatedValue = calculatedValue!!
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
        ) : TrackableValuePublishingContext {

            override fun update(
                transformer: Builder.() -> Builder
            ): TrackableValuePublishingContext {
                return transformer(
                        DefaultTrackableValuePublishingContextBuilder(
                            session,
                            dispatchedRequest,
                            publisher,
                            calculatedValue
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
