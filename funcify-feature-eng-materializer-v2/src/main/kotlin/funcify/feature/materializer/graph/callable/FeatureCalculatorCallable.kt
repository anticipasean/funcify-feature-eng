package funcify.feature.materializer.graph.callable

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface FeatureCalculatorCallable :
    (TrackableValue<JsonNode>, ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<
            TrackableValue<JsonNode>
        > {}
