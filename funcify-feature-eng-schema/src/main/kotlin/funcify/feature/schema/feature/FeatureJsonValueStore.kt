package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-24
 */
interface FeatureJsonValueStore {

    val name: String

    fun retrieveFromStore(
        plannedValue: TrackableValue.PlannedValue<JsonNode>
    ): Mono<TrackableValue<JsonNode>>

}
