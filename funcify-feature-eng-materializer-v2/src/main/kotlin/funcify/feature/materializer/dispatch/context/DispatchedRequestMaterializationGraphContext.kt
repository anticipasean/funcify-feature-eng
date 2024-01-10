package funcify.feature.materializer.dispatch.context

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.tracking.TrackableValue.PlannedValue
import graphql.execution.CoercedVariables
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-09-18
 */
interface DispatchedRequestMaterializationGraphContext {

    val rawGraphQLRequest: RawGraphQLRequest

    val materializationMetamodel: MaterializationMetamodel

    val requestMaterializationGraph: RequestMaterializationGraph

    val coercedVariables: CoercedVariables

    val rawInputContext: Option<RawInputContext>

    val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>

    val transformerPublishersByResultPath: ImmutableMap<GQLResultPath, Mono<JsonNode>>

    val dataElementPublishersByOperationPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val dataElementPublishersByResultPath: ImmutableMap<GQLResultPath, Mono<JsonNode>>

    val plannedFeatureValuesByPath:
        ImmutableMap<GQLOperationPath, ImmutableList<PlannedValue<JsonNode>>>

    val featureCalculatorPublishersByOperationPath:
        ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>

    val featureCalculatorPublishersByResultPath:
        ImmutableMap<GQLResultPath, Mono<TrackableValue<JsonNode>>>

    val passThruColumns: ImmutableMap<String, JsonNode>

    fun update(transformer: Builder.() -> Builder): DispatchedRequestMaterializationGraphContext

    interface Builder {

        fun rawGraphQLRequest(rawGraphQLRequest: RawGraphQLRequest): Builder

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): Builder

        fun requestMaterializationGraph(
            requestMaterializationGraph: RequestMaterializationGraph
        ): Builder

        fun rawInputContext(rawInputContext: RawInputContext): Builder

        fun coercedVariables(coercedVariables: CoercedVariables): Builder

        fun addMaterializedArgument(path: GQLOperationPath, jsonValue: JsonNode): Builder

        fun addAllMaterializedArguments(
            pathJsonValuePairs: Map<GQLOperationPath, JsonNode>
        ): Builder

        fun addTransformerPublisherForResultPath(
            path: GQLResultPath,
            publisher: Mono<JsonNode>
        ): Builder

        fun addAllTransformerPublishersForResultPaths(
            pathPublisherPairs: Map<GQLResultPath, Mono<JsonNode>>
        ): Builder

        fun addDataElementPublisherForOperationPath(
            path: GQLOperationPath,
            publisher: Mono<JsonNode>
        ): Builder

        fun addAllDataElementPublishersForOperationPaths(
            pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
        ): Builder

        fun addDataElementPublisherForResultPath(
            path: GQLResultPath,
            publisher: Mono<JsonNode>
        ): Builder

        fun addAllDataElementPublishersForResultPaths(
            pathPublisherPairs: Map<GQLResultPath, Mono<JsonNode>>
        ): Builder

        fun addPlannedFeatureValue(
            path: GQLOperationPath,
            plannedValue: PlannedValue<JsonNode>
        ): Builder

        fun addFeatureCalculatorPublisherForOperationPath(
            path: GQLOperationPath,
            publisher: Mono<TrackableValue<JsonNode>>
        ): Builder

        fun addAllFeatureCalculatorPublishersForOperationPaths(
            pathPublisherPairs: Map<GQLOperationPath, List<Mono<TrackableValue<JsonNode>>>>
        ): Builder

        fun addFeatureCalculatorPublisherForResultPath(
            path: GQLResultPath,
            publisher: Mono<TrackableValue<JsonNode>>
        ): Builder

        fun addAllFeatureCalculatorPublishersForResultPaths(
            pathPublisherPairs: Map<GQLResultPath, Mono<TrackableValue<JsonNode>>>
        ): Builder

        fun addPassThruColumn(columnName: String, columnValue: JsonNode): Builder

        fun addAllPassThruColumns(columns: Map<String, JsonNode>): Builder

        fun addAllPassThruColumns(columns: Iterable<Pair<String, JsonNode>>): Builder

        fun build(): DispatchedRequestMaterializationGraphContext
    }
}
