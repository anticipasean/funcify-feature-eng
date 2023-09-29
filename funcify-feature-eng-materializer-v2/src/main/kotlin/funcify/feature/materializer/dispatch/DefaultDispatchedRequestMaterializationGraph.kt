package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

data class DefaultDispatchedRequestMaterializationGraph(
    override val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
    override val transformerPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
    override val dataElementPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
    override val plannedFeatureValuesByPath: ImmutableMap<GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>>,
    override val featureCalculatorPublishersByPath: ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>,
    override val passThruColumns: ImmutableMap<String, JsonNode>
                                                       ): DispatchedRequestMaterializationGraph {

}
