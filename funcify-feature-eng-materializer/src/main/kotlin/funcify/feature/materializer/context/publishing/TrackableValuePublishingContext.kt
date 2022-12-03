package funcify.feature.materializer.context.publishing

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableJsonValuePublisher
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.session.GraphQLSingleRequestSession

/**
 *
 * @author smccarron
 * @created 2022-11-14
 */
interface TrackableValuePublishingContext {

    val session: GraphQLSingleRequestSession

    val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch

    val publisher: TrackableJsonValuePublisher

    val calculatedValue: TrackableValue.CalculatedValue<JsonNode>

    fun update(transformer: Builder.() -> Builder): TrackableValuePublishingContext

    interface Builder {

        fun graphQLSingleRequestSession(session: GraphQLSingleRequestSession): Builder

        fun dispatchedRequest(
            dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
        ): Builder

        fun trackableJsonValuePublisher(publisher: TrackableJsonValuePublisher): Builder

        fun calculatedValue(calculatedValue: TrackableValue.CalculatedValue<JsonNode>): Builder

        fun build(): TrackableValuePublishingContext
    }
}
