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
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
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
            private var requestMaterializationGraph: RequestMaterializationGraph? =
                existingContext?.requestMaterializationGraph,
            private val variables: PersistentMap.Builder<String, JsonNode> =
                existingContext?.variables?.toPersistentMap()?.builder()
                    ?: persistentMapOf<String, JsonNode>().builder(),
            private var rawInputContext: RawInputContext? =
                existingContext?.rawInputContext?.orNull(),
            private val materializedArgumentsByPath:
                PersistentMap.Builder<GQLOperationPath, JsonNode> =
                existingContext?.materializedArgumentsByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            private val transformerPublishersByPath:
                PersistentMap.Builder<GQLOperationPath, Mono<JsonNode>> =
                existingContext?.transformerPublishersByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, Mono<JsonNode>>().builder(),
            private val dataElementPublishersByPath:
                PersistentMap.Builder<GQLOperationPath, Mono<JsonNode>> =
                existingContext?.dataElementPublishersByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, Mono<JsonNode>>().builder(),
            private val plannedFeatureValuesByPath:
                PersistentMap.Builder<
                    GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                > =
                existingContext?.plannedFeatureValuesByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<
                            GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                        >()
                        .builder(),
            private val featureCalculatorPublishersByPath:
                PersistentMap.Builder<
                    GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>
                > =
                existingContext?.featureCalculatorPublishersByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<
                            GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>
                        >()
                        .builder(),
            private val passThruColumns: PersistentMap.Builder<String, JsonNode> =
                existingContext?.passThruColumns?.toPersistentMap()?.builder()
                    ?: persistentMapOf<String, JsonNode>().builder()
        ) : Builder {

            override fun requestMaterializationGraph(
                requestMaterializationGraph: RequestMaterializationGraph
            ): Builder =
                this.apply { this.requestMaterializationGraph = requestMaterializationGraph }

            override fun variables(variables: Map<String, JsonNode>): Builder =
                this.apply { this.variables.putAll(variables) }

            override fun rawInputContext(rawInputContext: RawInputContext): Builder =
                this.apply { this.rawInputContext = rawInputContext }

            override fun addMaterializedArgument(
                path: GQLOperationPath,
                jsonValue: JsonNode
            ): Builder = this.apply { this.materializedArgumentsByPath.put(path, jsonValue) }

            override fun addAllMaterializedArguments(
                pathJsonValuePairs: Map<GQLOperationPath, JsonNode>
            ): Builder = this.apply { this.materializedArgumentsByPath.putAll(pathJsonValuePairs) }

            override fun addTransformerPublisher(
                path: GQLOperationPath,
                publisher: Mono<JsonNode>,
            ): Builder = this.apply { this.transformerPublishersByPath.put(path, publisher) }

            override fun addAllTransformerPublishers(
                pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
            ): Builder = this.apply { this.transformerPublishersByPath.putAll(pathPublisherPairs) }

            override fun addDataElementPublisher(
                path: GQLOperationPath,
                publisher: Mono<JsonNode>,
            ): Builder = this.apply { this.dataElementPublishersByPath.put(path, publisher) }

            override fun addAllDataElementPublishers(
                pathPublisherPairs: Map<GQLOperationPath, Mono<JsonNode>>
            ): Builder = this.apply { this.dataElementPublishersByPath.putAll(pathPublisherPairs) }

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

            override fun addFeatureCalculatorPublisher(
                path: GQLOperationPath,
                publisher: Mono<TrackableValue<JsonNode>>,
            ): Builder =
                this.apply {
                    this.featureCalculatorPublishersByPath.put(
                        path,
                        this.featureCalculatorPublishersByPath
                            .getOrElse(path, ::persistentListOf)
                            .toPersistentList()
                            .add(publisher)
                    )
                }

            override fun addAllFeatureCalculatorPublishers(
                pathPublisherPairs: Map<GQLOperationPath, List<Mono<TrackableValue<JsonNode>>>>
            ): Builder =
                this.apply {
                    pathPublisherPairs.foldLeft(this.featureCalculatorPublishersByPath) {
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

            override fun addPassThruColumn(columnName: String, columnValue: JsonNode): Builder =
                this.apply { this.passThruColumns.put(columnName, columnValue) }

            override fun addAllPassThruColumns(columns: Map<String, JsonNode>): Builder =
                this.apply { this.passThruColumns.putAll(columns) }

            override fun addAllPassThruColumns(columns: Iterable<Pair<String, JsonNode>>): Builder =
                this.apply { this.passThruColumns.putAll(columns) }

            override fun build(): DispatchedRequestMaterializationGraphContext {
                return eagerEffect<String, DispatchedRequestMaterializationGraphContext> {
                        ensureNotNull(requestMaterializationGraph) {
                            "%s not provided".format(RequestMaterializationGraph::class.simpleName)
                        }
                        DefaultDispatchedRequestMaterializationGraphContext(
                            requestMaterializationGraph = requestMaterializationGraph!!,
                            variables = variables.build(),
                            rawInputContext = rawInputContext.toOption(),
                            materializedArgumentsByPath = materializedArgumentsByPath.build(),
                            transformerPublishersByPath = transformerPublishersByPath.build(),
                            dataElementPublishersByPath = dataElementPublishersByPath.build(),
                            plannedFeatureValuesByPath = plannedFeatureValuesByPath.build(),
                            featureCalculatorPublishersByPath =
                                featureCalculatorPublishersByPath.build(),
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
            override val requestMaterializationGraph: RequestMaterializationGraph,
            override val variables: ImmutableMap<String, JsonNode>,
            override val rawInputContext: Option<RawInputContext>,
            override val materializedArgumentsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
            override val transformerPublishersByPath:
                ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
            override val dataElementPublishersByPath:
                ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
            override val plannedFeatureValuesByPath:
                ImmutableMap<
                    GQLOperationPath, ImmutableList<TrackableValue.PlannedValue<JsonNode>>
                >,
            override val featureCalculatorPublishersByPath:
                ImmutableMap<GQLOperationPath, ImmutableList<Mono<TrackableValue<JsonNode>>>>,
            override val passThruColumns: ImmutableMap<String, JsonNode>
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
