package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-08
 */
interface DispatchedRequestMaterializationGraph {

    val transformerPublishersByCoordinates: ImmutableMap<FieldCoordinates, Mono<JsonNode>>

    val dataElementPublishersByCoordinates: ImmutableMap<FieldCoordinates, Mono<JsonNode>>

    val featureCalculatorPublishersByCoordinates:
        ImmutableMap<FieldCoordinates, Mono<TrackableValue<JsonNode>>>

    interface Builder {

        fun addTransformerPublisher(
            coordinates: FieldCoordinates,
            publisher: Mono<JsonNode>
        ): Builder

        fun addAllTransformerPublishers(
            publisherPairs: Iterable<Pair<FieldCoordinates, Mono<JsonNode>>>
        ): Builder

        fun addDataElementPublisher(
            coordinates: FieldCoordinates,
            publisher: Mono<JsonNode>
        ): Builder

        fun addAllDataElementPublishers(
            publisherPairs: Iterable<Pair<FieldCoordinates, Mono<JsonNode>>>
        ): Builder

        fun addFeatureCalculatorPublisher(
            coordinates: FieldCoordinates,
            publisher: Mono<TrackableValue<JsonNode>>
        )

        fun addAllFeatureCalculatorPublishers(
            publisherPairs: Iterable<Pair<FieldCoordinates, Mono<TrackableValue<JsonNode>>>>
        )

        fun build(): DispatchedRequestMaterializationGraph
    }
}
