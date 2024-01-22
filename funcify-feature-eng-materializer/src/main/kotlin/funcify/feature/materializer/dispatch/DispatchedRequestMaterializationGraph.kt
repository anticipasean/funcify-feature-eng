package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-08
 */
interface DispatchedRequestMaterializationGraph {

    val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>

    val transformerPublishersByPath: ImmutableMap<GQLResultPath, Mono<out JsonNode>>

    val dataElementPublishersByPath: ImmutableMap<GQLResultPath, Mono<out JsonNode>>

    val featureCalculatorPublishersByPath:
        ImmutableMap<GQLResultPath, Mono<out TrackableValue<JsonNode>>>

    val passThruColumns: ImmutableMap<String, JsonNode>
}
