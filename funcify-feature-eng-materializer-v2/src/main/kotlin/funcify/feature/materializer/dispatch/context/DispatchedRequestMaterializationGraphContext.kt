package funcify.feature.materializer.dispatch.context

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.tracking.TrackableValue.PlannedValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-09-18
 */
interface DispatchedRequestMaterializationGraphContext {

    val requestMaterializationGraph: RequestMaterializationGraph

    val variables: ImmutableMap<String, JsonNode>

    val rawInputContext: Option<RawInputContext>

    val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>

    val transformerPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val dataElementPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val plannedFeatureValuesByPath:
        ImmutableMap<GQLOperationPath, ImmutableList<PlannedValue<JsonNode>>>

    val featureCalculatorPublishersByPath:
        ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>

    val passThruColumns: ImmutableMap<String, JsonNode>

    fun update(transformer: Builder.() -> Builder): DispatchedRequestMaterializationGraphContext

    interface Builder {

        fun requestMaterializationGraph(
            requestMaterializationGraph: RequestMaterializationGraph
        ): Builder

        fun variables(variables: Map<String, JsonNode>): Builder

        fun rawInputContext(rawInputContext: RawInputContext): Builder

        fun addMaterializedArgument(path: GQLOperationPath, jsonValue: JsonNode): Builder

        fun addAllMaterializedArguments(
            pathJsonValuePairs: Map<GQLOperationPath, JsonNode>
        ): Builder

        fun addTransformerPublisher(path: GQLOperationPath, publisher: Mono<JsonNode>): Builder

        fun addAllTransformerPublishers(
            pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
        ): Builder

        fun addDataElementPublisher(path: GQLOperationPath, publisher: Mono<JsonNode>): Builder

        fun addAllDataElementPublishers(
            pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
        ): Builder

        fun addPlannedFeatureValue(
            path: GQLOperationPath,
            plannedValue: PlannedValue<JsonNode>
        ): Builder

        fun addFeatureCalculatorPublisher(
            path: GQLOperationPath,
            publisher: Mono<TrackableValue<JsonNode>>
        ): Builder

        fun addAllFeatureCalculatorPublishers(
            pathPublisherPairs: Map<GQLOperationPath, Mono<TrackableValue<JsonNode>>>
        ): Builder

        fun addPassThruColumn(columnName: String, columnValue: JsonNode): Builder

        fun addAllPassThruColumns(columns: Map<String, JsonNode>): Builder

        fun addAllPassThruColumns(columns: Iterable<Pair<String, JsonNode>>): Builder

        fun build(): DispatchedRequestMaterializationGraphContext
    }
}
