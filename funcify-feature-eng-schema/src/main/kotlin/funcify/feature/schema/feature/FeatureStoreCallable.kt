package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface FeatureStoreCallable :
    (TrackableValue.PlannedValue<JsonNode>) -> Mono<TrackableValue<JsonNode>> {}
