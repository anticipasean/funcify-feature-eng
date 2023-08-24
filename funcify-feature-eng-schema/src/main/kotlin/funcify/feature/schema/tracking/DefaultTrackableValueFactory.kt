package funcify.feature.schema.tracking

import arrow.core.Option
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue.CalculatedValue
import funcify.feature.schema.tracking.TrackableValue.PlannedValue
import funcify.feature.schema.tracking.TrackableValue.TrackedValue
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.GraphQLOutputType
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

/**
 * @author smccarron
 * @created 2022-09-05
 */
internal class DefaultTrackableValueFactory : TrackableValueFactory {

    companion object {

        internal abstract class BaseBuilder<B : TrackableValue.Builder<B>, V>(
            protected open var targetSourceIndexPath: GQLOperationPath? = null,
            protected open var contextualParameters:
                PersistentMap.Builder<GQLOperationPath, JsonNode> =
                persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            protected open var graphQLOutputType: GraphQLOutputType? = null,
        ) : TrackableValue.Builder<B> {

            override fun targetSourceIndexPath(targetSourceIndexPath: GQLOperationPath): B {
                this.targetSourceIndexPath = targetSourceIndexPath
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun contextualParameters(
                contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>
            ): B {
                this.contextualParameters = contextualParameters.toPersistentMap().builder()
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun addContextualParameter(
                parameterPath: GQLOperationPath,
                parameterValue: JsonNode
            ): B {
                this.contextualParameters.put(parameterPath, parameterValue)
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun addContextualParameter(
                parameterPathValuePair: Pair<GQLOperationPath, JsonNode>
            ): B {
                this.contextualParameters.put(
                    parameterPathValuePair.first,
                    parameterPathValuePair.second
                )
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun removeContextualParameter(parameterPath: GQLOperationPath): B {
                this.contextualParameters.remove(parameterPath)
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun clearContextualParameters(): B {
                this.contextualParameters.clear()
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun addContextualParameters(
                contextualParameters: Map<GQLOperationPath, JsonNode>
            ): B {
                this.contextualParameters.putAll(contextualParameters)
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun graphQLOutputType(graphQLOutputType: GraphQLOutputType): B {
                this.graphQLOutputType = graphQLOutputType
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }
        }

        internal class DefaultPlannedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null
        ) :
            BaseBuilder<PlannedValue.Builder, V>(
                targetSourceIndexPath = existingPlannedValue?.targetSourceIndexPath,
                contextualParameters =
                    existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                        ?: persistentMapOf<GQLOperationPath, JsonNode>().builder(),
                graphQLOutputType = existingPlannedValue?.graphQLOutputType
            ),
            PlannedValue.Builder {

            override fun <V> buildForInstanceOf(): Try<PlannedValue<V>> {
                return when {
                    targetSourceIndexPath == null -> {
                        Try.failure(
                            ServiceError.of(
                                "target_source_index_path has not been set for planned_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            ServiceError.of(
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the planned_value"""
                                    .flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for planned_value""".flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultPlannedValue<V>(
                                targetSourceIndexPath = targetSourceIndexPath!!,
                                contextualParameters = contextualParameters.build(),
                                graphQLOutputType = graphQLOutputType!!,
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultCalculatedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null,
            override var targetSourceIndexPath: GQLOperationPath? =
                existingPlannedValue?.targetSourceIndexPath,
            override var contextualParameters: PersistentMap.Builder<GQLOperationPath, JsonNode> =
                existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            override var graphQLOutputType: GraphQLOutputType? =
                existingPlannedValue?.graphQLOutputType,
            private var calculatedValue: V? = null,
            private var calculatedTimestamp: Instant? = null,
        ) :
            BaseBuilder<CalculatedValue.Builder<V>, V>(
                targetSourceIndexPath = targetSourceIndexPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            CalculatedValue.Builder<V> {

            override fun calculatedValue(calculatedValue: V): DefaultCalculatedValueBuilder<V> {
                this.calculatedValue = calculatedValue
                return this
            }

            override fun calculatedTimestamp(
                calculatedTimestamp: Instant
            ): DefaultCalculatedValueBuilder<V> {
                this.calculatedTimestamp = calculatedTimestamp
                return this
            }

            override fun build(): Try<CalculatedValue<V>> {
                return when {
                    targetSourceIndexPath == null -> {
                        Try.failure(
                            ServiceError.of(
                                "target_source_index_path has not been set for calculated_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            ServiceError.of(
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the calculated_value"""
                                    .flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for calculated_value""".flatten()
                            )
                        )
                    }
                    calculatedValue == null || calculatedTimestamp == null -> {
                        Try.failure(
                            ServiceError.of(
                                """calculated_value and calculated_timestamp 
                                    |must be provided for calculated_value creation"""
                                    .flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultCalculatedValue<V>(
                                targetSourceIndexPath!!,
                                contextualParameters.build(),
                                graphQLOutputType!!,
                                calculatedValue!!,
                                calculatedTimestamp!!
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultTrackedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null,
            private val existingCalculatedValue: CalculatedValue<V>? = null,
            override var targetSourceIndexPath: GQLOperationPath? =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::targetSourceIndexPath)
                    .orElse {
                        existingCalculatedValue
                            .toOption()
                            .map(CalculatedValue<V>::targetSourceIndexPath)
                    }
                    .orNull(),
            override var contextualParameters: PersistentMap.Builder<GQLOperationPath, JsonNode> =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::contextualParameters)
                    .orElse {
                        existingCalculatedValue
                            .toOption()
                            .map(CalculatedValue<V>::contextualParameters)
                    }
                    .map { m -> m.toPersistentMap().builder() }
                    .orNull()
                    ?: persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            override var graphQLOutputType: GraphQLOutputType? =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::graphQLOutputType)
                    .orElse {
                        existingCalculatedValue
                            .toOption()
                            .map(CalculatedValue<V>::graphQLOutputType)
                    }
                    .orNull(),
            private var canonicalPath: GQLOperationPath? = null,
            private var referencePaths: PersistentSet.Builder<GQLOperationPath> =
                persistentSetOf<GQLOperationPath>().builder(),
            private var trackedValue: V? = null,
            private var valueAtTimestamp: Instant? = null,
        ) :
            BaseBuilder<TrackedValue.Builder<V>, V>(
                targetSourceIndexPath = targetSourceIndexPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            TrackedValue.Builder<V> {

            override fun canonicalPath(
                canonicalPath: GQLOperationPath
            ): DefaultTrackedValueBuilder<V> {
                this.canonicalPath = canonicalPath
                return this
            }

            override fun referencePaths(
                referencePaths: ImmutableSet<GQLOperationPath>
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths = referencePaths.toPersistentSet().builder()
                return this
            }

            override fun addReferencePath(
                referencePath: GQLOperationPath
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.add(referencePath)
                return this
            }

            override fun removeReferencePath(
                referencePath: GQLOperationPath
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.remove(referencePath)
                return this
            }

            override fun clearReferencePaths(): DefaultTrackedValueBuilder<V> {
                this.referencePaths.clear()
                return this
            }

            override fun addReferencePaths(
                referencePaths: Iterable<GQLOperationPath>
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.addAll(referencePaths)
                return this
            }

            override fun trackedValue(trackedValue: V): DefaultTrackedValueBuilder<V> {
                this.trackedValue = trackedValue
                return this
            }

            override fun valueAtTimestamp(
                valueAtTimestamp: Instant
            ): DefaultTrackedValueBuilder<V> {
                this.valueAtTimestamp = valueAtTimestamp
                return this
            }

            override fun build(): Try<TrackedValue<V>> {
                return when {
                    targetSourceIndexPath == null -> {
                        Try.failure(
                            ServiceError.of(
                                "target_source_index_path has not been set for tracked_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            ServiceError.of(
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the tracked_value"""
                                    .flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for tracked_value""".flatten()
                            )
                        )
                    }
                    trackedValue == null || valueAtTimestamp == null -> {
                        Try.failure(
                            ServiceError.of(
                                """both a tracked_value and value_at_timestamp 
                                |must be provided for creation of a tracked_value"""
                                    .flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultTrackedValue<V>(
                                targetSourceIndexPath!!,
                                canonicalPath.toOption(),
                                referencePaths.build(),
                                contextualParameters.build(),
                                graphQLOutputType!!,
                                trackedValue!!,
                                valueAtTimestamp!!
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal data class DefaultPlannedValue<V>(
            override val targetSourceIndexPath: GQLOperationPath,
            override val contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType
        ) : PlannedValue<V> {

            override fun update(
                transformer: PlannedValue.Builder.() -> PlannedValue.Builder
            ): PlannedValue<V> {
                @Suppress("UNCHECKED_CAST") //
                return transformer(DefaultPlannedValueBuilder<V>(existingPlannedValue = this))
                    .buildForInstanceOf<V>()
                    .orElse(this)
            }

            override fun transitionToCalculated(
                mapper: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
            ): TrackableValue<V> {
                @Suppress("UNCHECKED_CAST") //
                return mapper(DefaultCalculatedValueBuilder<V>(existingPlannedValue = this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }

            override fun transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackableValue<V> {
                return mapper(DefaultTrackedValueBuilder<V>(existingPlannedValue = this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }
        }

        internal data class DefaultCalculatedValue<V>(
            override val targetSourceIndexPath: GQLOperationPath,
            override val contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val calculatedValue: V,
            override val calculatedTimestamp: Instant
        ) : CalculatedValue<V> {

            override fun update(
                transformer: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
            ): CalculatedValue<V> {
                return transformer(
                        DefaultCalculatedValueBuilder<V>(
                            existingPlannedValue = null,
                            targetSourceIndexPath = targetSourceIndexPath,
                            contextualParameters = contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType = graphQLOutputType,
                            calculatedValue = calculatedValue,
                            calculatedTimestamp = calculatedTimestamp
                        )
                    )
                    .build()
                    .orElse(this)
            }

            override fun transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackableValue<V> {
                return mapper(DefaultTrackedValueBuilder<V>(null, this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElse(this)
            }
        }

        internal data class DefaultTrackedValue<V>(
            override val targetSourceIndexPath: GQLOperationPath,
            override val canonicalPath: Option<GQLOperationPath>,
            override val referencePaths: ImmutableSet<GQLOperationPath>,
            override val contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val trackedValue: V,
            override val valueAtTimestamp: Instant
        ) : TrackedValue<V> {

            override fun update(
                transformer: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackedValue<V> {

                return transformer(
                        DefaultTrackedValueBuilder<V>(
                            null,
                            null,
                            targetSourceIndexPath,
                            contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType,
                            canonicalPath.orNull(),
                            referencePaths.toPersistentSet().builder(),
                            trackedValue,
                            valueAtTimestamp
                        )
                    )
                    .build()
                    .orElse(this)
            }
        }
    }

    override fun builder(): PlannedValue.Builder {
        return DefaultPlannedValueBuilder<Any?>()
    }
}
