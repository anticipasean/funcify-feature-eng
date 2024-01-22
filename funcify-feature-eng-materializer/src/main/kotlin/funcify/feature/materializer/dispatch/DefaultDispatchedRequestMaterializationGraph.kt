package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

data class DefaultDispatchedRequestMaterializationGraph(
    override val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
    override val transformerPublishersByPath: ImmutableMap<GQLResultPath, Mono<out JsonNode>>,
    override val dataElementPublishersByPath: ImmutableMap<GQLResultPath, Mono<out JsonNode>>,
    override val featureCalculatorPublishersByPath:
        ImmutableMap<GQLResultPath, Mono<out TrackableValue<JsonNode>>>,
    override val passThruColumns: ImmutableMap<String, JsonNode>
) : DispatchedRequestMaterializationGraph {}
