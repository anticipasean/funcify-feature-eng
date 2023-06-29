package funcify.feature.schema.dataelementsource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface FeatureJsonValueStore :
        (TrackableValue<JsonNode>) -> Mono<TrackableValue<JsonNode>> {

    val cacheForDataSource: DataElementSource

    override fun invoke(trackableValue: TrackableValue<JsonNode>): Mono<TrackableValue<JsonNode>>

    interface Builder {

        fun cacheForDataSource(dataSource: DataElementSource): Builder

        fun build(): Try<FeatureJsonValueStore>
    }
}
