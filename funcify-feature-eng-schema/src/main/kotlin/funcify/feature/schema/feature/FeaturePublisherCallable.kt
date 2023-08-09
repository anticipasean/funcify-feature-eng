package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-08-05
 */
interface FeaturePublisherCallable : (TrackableValue<JsonNode>) -> Mono<TrackableValue<JsonNode>> {}
