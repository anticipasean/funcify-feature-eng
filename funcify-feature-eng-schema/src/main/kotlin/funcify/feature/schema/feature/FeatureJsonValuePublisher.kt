package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-09-05
 */
interface FeatureJsonValuePublisher {

    val name: String

    fun publishToStore(
        featureJsonValue: TrackableValue<JsonNode>
    ): Mono<out TrackableValue<JsonNode>>
}
