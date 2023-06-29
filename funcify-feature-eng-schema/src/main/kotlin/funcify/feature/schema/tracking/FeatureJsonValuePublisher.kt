package funcify.feature.schema.tracking

import com.fasterxml.jackson.databind.JsonNode

/**
 * @author smccarron
 * @created 2022-09-05
 */
fun interface FeatureJsonValuePublisher {

    // TODO: Consider updating contract with return type Mono<Unit|Void> to
    // signal failure or completion to consumer
    fun publish(featureJsonValue: TrackableValue<JsonNode>)
}
