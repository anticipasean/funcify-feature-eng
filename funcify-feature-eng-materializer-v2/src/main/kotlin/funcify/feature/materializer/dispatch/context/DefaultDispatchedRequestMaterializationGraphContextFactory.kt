package funcify.feature.materializer.dispatch.context

import arrow.core.Option
import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.foldLeft
import arrow.core.identity
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.context.DispatchedRequestMaterializationGraphContext.Builder
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import graphql.execution.CoercedVariables
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-09-18
 */
internal class DefaultDispatchedRequestMaterializationGraphContextFactory :
    DispatchedRequestMaterializationGraphContextFactory {

    companion object {
        internal class DefaultBuilder(
            private val existingContext: DefaultDispatchedRequestMaterializationGraphContext? =
                null,
            private var rawGraphQLRequest: RawGraphQLRequest? = existingContext?.rawGraphQLRequest,
            private var materializationMetamodel: MaterializationMetamodel? =
                existingContext?.materializationMetamodel,
            private var requestMaterializationGraph: RequestMaterializationGraph? =
                existingContext?.requestMaterializationGraph,
            private var coercedVariables: CoercedVariables =
                existingContext?.coercedVariables ?: CoercedVariables.emptyVariables(),
            private var rawInputContext: RawInputContext? =
                existingContext?.rawInputContext?.orNull(),
            private val materializedArgumentsByPath:
                PersistentMap.Builder<GQLOperationPath, JsonNode> =
                existingContext?.materializedArgumentsByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            private val transformerPublishersByPath:
                PersistentMap.Builder<GQLResultPath, Mono<JsonNode>> =
                existingContext?.transformerPublishersByResultPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLResultPath, Mono<JsonNode>>().builder(),
            private val dataElementPublishersByOperationPath:
                PersistentMap.Builder<GQLOperationPath, Mono<JsonNode>> =
                existingContext?.dataElementPublishersByOperationPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, Mono<JsonNode>>().builder(),
            private val dataElementPublishersByResultPath:
                PersistentMap.Builder<GQLResultPath, Mono<JsonNode>> =
                existingContext?.dataElementPublishersByResultPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLResultPath, Mono<JsonNode>>().builder(),
            private val plannedFeatureValuesByPath:
                PersistentMap.Builder<
                    GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                > =
                existingContext?.plannedFeatureValuesByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<
                            GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                        >()
                        .builder(),
            private val featureCalculatorPublishersByOperationPath:
                PersistentMap.Builder<
                    GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>
                > =
                existingContext
                    ?.featureCalculatorPublishersByOperationPath
                    ?.toPersistentMap()
                    ?.builder()
                    ?: persistentMapOf<
                            GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>
                        >()
                        .builder(),
            private val featureCalculatorPublishersByResultPath:
                PersistentMap.Builder<GQLResultPath, Mono<TrackableValue<JsonNode>>> =
                existingContext
                    ?.featureCalculatorPublishersByResultPath
                    ?.toPersistentMap()
                    ?.builder()
                    ?: persistentMapOf<GQLResultPath, Mono<TrackableValue<JsonNode>>>().builder(),
            private val passThruColumns: PersistentMap.Builder<String, JsonNode> =
                existingContext?.passThruColumns?.toPersistentMap()?.builder()
                    ?: persistentMapOf<String, JsonNode>().builder()
        ) : Builder {

            override fun rawGraphQLRequest(rawGraphQLRequest: RawGraphQLRequest): Builder =
                this.apply { this.rawGraphQLRequest = rawGraphQLRequest }

            override fun materializationMetamodel(
                materializationMetamodel: MaterializationMetamodel
            ): Builder = this.apply { this.materializationMetamodel = materializationMetamodel }

            override fun requestMaterializationGraph(
                requestMaterializationGraph: RequestMaterializationGraph
            ): Builder =
                this.apply { this.requestMaterializationGraph = requestMaterializationGraph }

            override fun coercedVariables(coercedVariables: CoercedVariables): Builder =
                this.apply { this.coercedVariables = coercedVariables }

            override fun rawInputContext(rawInputContext: RawInputContext): Builder =
                this.apply { this.rawInputContext = rawInputContext }

            override fun addMaterializedArgument(
                path: GQLOperationPath,
                jsonValue: JsonNode
            ): Builder = this.apply { this.materializedArgumentsByPath.put(path, jsonValue) }

            override fun addAllMaterializedArguments(
                pathJsonValuePairs: Map<GQLOperationPath, JsonNode>
            ): Builder = this.apply { this.materializedArgumentsByPath.putAll(pathJsonValuePairs) }

            override fun addTransformerPublisherForResultPath(
                path: GQLResultPath,
                publisher: Mono<JsonNode>,
            ): Builder = this.apply { this.transformerPublishersByPath.put(path, publisher) }

            override fun addAllTransformerPublishersForResultPaths(
                pathPublisherPairs: Map<GQLResultPath, Mono<JsonNode>>
            ): Builder = this.apply { this.transformerPublishersByPath.putAll(pathPublisherPairs) }

            override fun addDataElementPublisherForOperationPath(
                path: GQLOperationPath,
                publisher: Mono<JsonNode>,
            ): Builder =
                this.apply { this.dataElementPublishersByOperationPath.put(path, publisher) }

            override fun addAllDataElementPublishersForOperationPaths(
                pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
            ): Builder =
                this.apply { this.dataElementPublishersByOperationPath.putAll(pathPublisherPairs) }

            override fun addDataElementPublisherForResultPath(
                path: GQLResultPath,
                publisher: Mono<JsonNode>,
            ): Builder = this.apply { this.dataElementPublishersByResultPath.put(path, publisher) }

            override fun addAllDataElementPublishersForResultPaths(
                pathPublisherPairs: Map<GQLResultPath, Mono<JsonNode>>
            ): Builder =
                this.apply { this.dataElementPublishersByResultPath.putAll(pathPublisherPairs) }

            override fun addPlannedFeatureValue(
                path: GQLOperationPath,
                plannedValue: TrackableValue.PlannedValue<JsonNode>,
            ): Builder =
                this.apply {
                    this.plannedFeatureValuesByPath.put(
                        path,
                        this.plannedFeatureValuesByPath
                            .getOrElse(path, ::persistentListOf)
                            .toPersistentList()
                            .add(plannedValue)
                    )
                }

            override fun addFeatureCalculatorPublisherForOperationPath(
                path: GQLOperationPath,
                publisher: Mono<TrackableValue<JsonNode>>,
            ): Builder =
                this.apply {
                    this.featureCalculatorPublishersByOperationPath.put(
                        path,
                        this.featureCalculatorPublishersByOperationPath
                            .getOrElse(path, ::persistentListOf)
                            .toPersistentList()
                            .add(publisher)
                    )
                }

            override fun addAllFeatureCalculatorPublishersForOperationPaths(
                pathPublisherPairs: Map<GQLOperationPath, List<Mono<TrackableValue<JsonNode>>>>
            ): Builder =
                this.apply {
                    pathPublisherPairs.foldLeft(this.featureCalculatorPublishersByOperationPath) {
                        builder:
                            PersistentMap.Builder<
                                GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>
                            >,
                        (key: GQLOperationPath, value: List<Mono<TrackableValue<JsonNode>>>) ->
                        builder.put(
                            key,
                            builder
                                .getOrElse(key, ::persistentListOf)
                                .toPersistentList()
                                .addAll(value)
                        )
                        builder
                    }
                }

            override fun addFeatureCalculatorPublisherForResultPath(
                path: GQLResultPath,
                publisher: Mono<TrackableValue<JsonNode>>,
            ): Builder =
                this.apply { this.featureCalculatorPublishersByResultPath.put(path, publisher) }

            override fun addAllFeatureCalculatorPublishersForResultPaths(
                pathPublisherPairs: Map<GQLResultPath, Mono<TrackableValue<JsonNode>>>
            ): Builder =
                this.apply {
                    pathPublisherPairs.foldLeft(this.featureCalculatorPublishersByResultPath) {
                        builder:
                            PersistentMap.Builder<GQLResultPath, Mono<TrackableValue<JsonNode>>>,
                        (key: GQLResultPath, value: Mono<TrackableValue<JsonNode>>) ->
                        builder.put(key, value)
                        builder
                    }
                }

            override fun addPassThruColumn(columnName: String, columnValue: JsonNode): Builder =
                this.apply { this.passThruColumns.put(columnName, columnValue) }

            override fun addAllPassThruColumns(columns: Map<String, JsonNode>): Builder =
                this.apply { this.passThruColumns.putAll(columns) }

            override fun addAllPassThruColumns(columns: Iterable<Pair<String, JsonNode>>): Builder =
                this.apply { this.passThruColumns.putAll(columns) }

            override fun build(): DispatchedRequestMaterializationGraphContext {
                return eagerEffect<String, DispatchedRequestMaterializationGraphContext> {
                        ensureNotNull(rawGraphQLRequest) {
                            "%s not provided".format(RawGraphQLRequest::class.simpleName)
                        }
                        ensureNotNull(materializationMetamodel) {
                            "%s not provided".format(MaterializationMetamodel::class.simpleName)
                        }
                        ensureNotNull(requestMaterializationGraph) {
                            "%s not provided".format(RequestMaterializationGraph::class.simpleName)
                        }
                        DefaultDispatchedRequestMaterializationGraphContext(
                            rawGraphQLRequest = rawGraphQLRequest!!,
                            materializationMetamodel = materializationMetamodel!!,
                            requestMaterializationGraph = requestMaterializationGraph!!,
                            coercedVariables = coercedVariables,
                            rawInputContext = rawInputContext.toOption(),
                            materializedArgumentsByPath = materializedArgumentsByPath.build(),
                            transformerPublishersByResultPath = transformerPublishersByPath.build(),
                            dataElementPublishersByOperationPath =
                                dataElementPublishersByOperationPath.build(),
                            dataElementPublishersByResultPath =
                                dataElementPublishersByResultPath.build(),
                            plannedFeatureValuesByPath = plannedFeatureValuesByPath.build(),
                            featureCalculatorPublishersByOperationPath =
                                featureCalculatorPublishersByOperationPath.build(),
                            featureCalculatorPublishersByResultPath =
                                featureCalculatorPublishersByResultPath.build(),
                            passThruColumns = passThruColumns.build(),
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "error occurred when creating %s [ message: %s ]",
                                DispatchedRequestMaterializationGraphContext::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }

        internal class DefaultDispatchedRequestMaterializationGraphContext(
            override val rawGraphQLRequest: RawGraphQLRequest,
            override val materializationMetamodel: MaterializationMetamodel,
            override val requestMaterializationGraph: RequestMaterializationGraph,
            override val coercedVariables: CoercedVariables,
            override val rawInputContext: Option<RawInputContext>,
            override val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
            override val transformerPublishersByResultPath:
                ImmutableMap<GQLResultPath, Mono<JsonNode>>,
            override val dataElementPublishersByOperationPath:
                ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
            override val dataElementPublishersByResultPath:
                ImmutableMap<GQLResultPath, Mono<JsonNode>>,
            override val plannedFeatureValuesByPath:
                ImmutableMap<
                    GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                >,
            override val featureCalculatorPublishersByOperationPath:
                ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>,
            override val featureCalculatorPublishersByResultPath:
                ImmutableMap<GQLResultPath, Mono<TrackableValue<JsonNode>>>,
            override val passThruColumns: ImmutableMap<String, JsonNode>,
        ) : DispatchedRequestMaterializationGraphContext {

            override fun update(
                transformer: Builder.() -> Builder
            ): DispatchedRequestMaterializationGraphContext {
                return transformer.invoke(DefaultBuilder(this)).build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder()
    }
}
