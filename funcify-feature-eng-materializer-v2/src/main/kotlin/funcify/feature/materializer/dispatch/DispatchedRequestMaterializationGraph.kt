package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
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

    val transformerPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val dataElementPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val plannedFeatureValuesByPath:
        ImmutableMap<GQLOperationPath, ImmutableList<PlannedValue<JsonNode>>>

    val featureCalculatorPublishersByPath:
        ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>

    val passThruColumns: ImmutableMap<String, JsonNode>
}
