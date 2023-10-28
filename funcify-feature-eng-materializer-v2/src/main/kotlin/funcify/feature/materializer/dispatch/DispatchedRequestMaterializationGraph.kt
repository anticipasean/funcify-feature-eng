package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.tracking.TrackableValue.PlannedValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-08
 */
interface DispatchedRequestMaterializationGraph {

    val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>

    val transformerPublishersByPath: ImmutableMap<GQLResultPath, Mono<JsonNode>>

    val dataElementPublishersByPath: ImmutableMap<GQLResultPath, Mono<JsonNode>>

    val featureCalculatorPublishersByPath:
        ImmutableMap<GQLResultPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>

    val passThruColumns: ImmutableMap<String, JsonNode>
}
