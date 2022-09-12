package funcify.feature.datasource.tracking

import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.tracking.TrackableValue.CalculatedValue
import funcify.feature.datasource.tracking.TrackableValue.PlannedValue
import funcify.feature.datasource.tracking.TrackableValue.TrackedValue
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.GraphQLOutputType
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
internal class DefaultTrackableValueFactory : TrackableValueFactory {

    companion object {

        internal abstract class BaseBuilder<B : TrackableValue.Builder<B>, V>(
            protected open var sourceIndexPath: SchematicPath? = null,
            protected open var contextualParameters:
                PersistentMap.Builder<SchematicPath, JsonNode> =
                persistentMapOf<SchematicPath, JsonNode>().builder(),
            protected open var graphQLOutputType: GraphQLOutputType? = null,
        ) : TrackableValue.Builder<B> {

            override fun sourceIndexPath(sourceIndexPath: SchematicPath): B {
                this.sourceIndexPath = sourceIndexPath
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun contextualParameters(
                contextualParameters: ImmutableMap<SchematicPath, JsonNode>
            ): B {
                this.contextualParameters = contextualParameters.toPersistentMap().builder()
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun addContextualParameter(
                parameterPath: SchematicPath,
                parameterValue: JsonNode
            ): B {
                this.contextualParameters.put(parameterPath, parameterValue)
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun addContextualParameter(
                parameterPathValuePair: Pair<SchematicPath, JsonNode>
            ): B {
                this.contextualParameters.put(
                    parameterPathValuePair.first,
                    parameterPathValuePair.second
                )
                @Suppress("UNCHECKED_CAST") //
                return this as B
            }

            override fun removeContextualParameter(parameterPath: SchematicPath): B {
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
                contextualParameters: Map<SchematicPath, JsonNode>
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
            BaseBuilder<DefaultPlannedValueBuilder<V>, V>(
                sourceIndexPath = existingPlannedValue?.sourceIndexPath,
                contextualParameters =
                    existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                        ?: persistentMapOf<SchematicPath, JsonNode>().builder(),
                graphQLOutputType = existingPlannedValue?.graphQLOutputType
            ),
            PlannedValue.Builder<DefaultPlannedValueBuilder<V>> {

            override fun <V> buildForTracking(): Try<PlannedValue<V>> {
                return when {
                    sourceIndexPath == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                "source_index_path has not been set for planned_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the planned_value""".flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """graphql_output_type must be provided for planned_value""".flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultPlannedValue<V>(
                                sourceIndexPath = sourceIndexPath!!,
                                contextualParameters = contextualParameters.build(),
                                graphQLOutputType = graphQLOutputType!!,
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultCalculatedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>?,
            override var sourceIndexPath: SchematicPath? = existingPlannedValue?.sourceIndexPath,
            override var contextualParameters: PersistentMap.Builder<SchematicPath, JsonNode> =
                existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                    ?: persistentMapOf<SchematicPath, JsonNode>().builder(),
            override var graphQLOutputType: GraphQLOutputType? =
                existingPlannedValue?.graphQLOutputType,
            private var calculatedValue: V? = null,
            private var calculatedTimestamp: Instant? = null,
        ) :
            BaseBuilder<DefaultCalculatedValueBuilder<V>, V>(
                sourceIndexPath = sourceIndexPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            CalculatedValue.Builder<DefaultCalculatedValueBuilder<V>, V> {

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
                    sourceIndexPath == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                "source_index_path has not been set for calculated_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the calculated_value""".flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """graphql_output_type must be provided for calculated_value""".flatten()
                            )
                        )
                    }
                    calculatedValue == null || calculatedTimestamp == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """calculated_value and calculated_timestamp 
                                    |must be provided for calculated_value creation""".flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultCalculatedValue<V>(
                                sourceIndexPath!!,
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
            override var sourceIndexPath: SchematicPath? =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::sourceIndexPath)
                    .orElse {
                        existingCalculatedValue.toOption().map(CalculatedValue<V>::sourceIndexPath)
                    }
                    .orNull(),
            override var contextualParameters: PersistentMap.Builder<SchematicPath, JsonNode> =
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
                    ?: persistentMapOf<SchematicPath, JsonNode>().builder(),
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
            private var trackedValue: V? = null,
            private var valueAtTimestamp: Instant? = null,
        ) :
            BaseBuilder<DefaultTrackedValueBuilder<V>, V>(
                sourceIndexPath = sourceIndexPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            TrackedValue.Builder<DefaultTrackedValueBuilder<V>, V> {

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
                    sourceIndexPath == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                "source_index_path has not been set for planned_value"
                            )
                        )
                    }
                    contextualParameters.isEmpty() -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one contextual_parameter 
                                    |must be provided for association and/or identification 
                                    |of the planned_value""".flatten()
                            )
                        )
                    }
                    graphQLOutputType == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """graphql_output_type must be provided for planned_value""".flatten()
                            )
                        )
                    }
                    trackedValue == null || valueAtTimestamp == null -> {
                        Try.failure(
                            DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """both a tracked_value and value_at_timestamp 
                                |must be provided for creation of a tracked_value""".flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultTrackedValue<V>(
                                sourceIndexPath!!,
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
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType
        ) : PlannedValue<V> {

            override fun <B1, B2> update(
                transformer: PlannedValue.Builder<B1>.() -> PlannedValue.Builder<B2>
            ): PlannedValue<V> where B1 : PlannedValue.Builder<B1>, B2 : PlannedValue.Builder<B2> {
                @Suppress("UNCHECKED_CAST") //
                return transformer(
                        DefaultPlannedValueBuilder<V>(existingPlannedValue = this)
                            as PlannedValue.Builder<B1>
                    )
                    .buildForTracking<V>()
                    .orElse(this)
            }

            override fun <B1, B2> transitionToCalculated(
                mapper: CalculatedValue.Builder<B1, V>.() -> CalculatedValue.Builder<B2, V>
            ): TrackableValue<V> where
            B1 : CalculatedValue.Builder<B1, V>,
            B2 : CalculatedValue.Builder<B2, V> {
                @Suppress("UNCHECKED_CAST") //
                return mapper(
                        DefaultCalculatedValueBuilder<V>(this) as CalculatedValue.Builder<B1, V>
                    )
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }

            override fun <B1, B2> transitionToTracked(
                mapper: TrackedValue.Builder<B1, V>.() -> TrackedValue.Builder<B2, V>
            ): TrackableValue<V> where
            B1 : TrackedValue.Builder<B1, V>,
            B2 : TrackedValue.Builder<B2, V> {
                @Suppress("UNCHECKED_CAST") //
                return mapper(DefaultTrackedValueBuilder<V>(this) as TrackedValue.Builder<B1, V>)
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }
        }

        internal data class DefaultCalculatedValue<V>(
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val calculatedValue: V,
            override val calculatedTimestamp: Instant
        ) : CalculatedValue<V> {

            override fun <B1, B2> update(
                transformer: CalculatedValue.Builder<B1, V>.() -> CalculatedValue.Builder<B2, V>
            ): CalculatedValue<V> where
            B1 : CalculatedValue.Builder<B1, V>,
            B2 : CalculatedValue.Builder<B2, V> {
                @Suppress("UNCHECKED_CAST") //
                return transformer(
                        DefaultCalculatedValueBuilder<V>(
                            existingPlannedValue = null,
                            sourceIndexPath = sourceIndexPath,
                            contextualParameters = contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType = graphQLOutputType,
                            calculatedValue = calculatedValue,
                            calculatedTimestamp = calculatedTimestamp
                        )
                            as CalculatedValue.Builder<B1, V>
                    )
                    .build()
                    .orElse(this)
            }

            override fun <B1, B2> transitionToTracked(
                mapper: TrackedValue.Builder<B1, V>.() -> TrackedValue.Builder<B2, V>
            ): TrackableValue<V> where
            B1 : TrackedValue.Builder<B1, V>,
            B2 : TrackedValue.Builder<B2, V> {
                @Suppress("UNCHECKED_CAST") //
                return mapper(
                        DefaultTrackedValueBuilder<V>(null, this) as TrackedValue.Builder<B1, V>
                    )
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElse(this)
            }
        }

        internal data class DefaultTrackedValue<V>(
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val trackedValue: V,
            override val valueAtTimestamp: Instant
        ) : TrackedValue<V> {

            override fun <B1, B2> update(
                transformer: TrackedValue.Builder<B1, V>.() -> TrackedValue.Builder<B2, V>
            ): TrackedValue<V> where
            B1 : TrackedValue.Builder<B1, V>,
            B2 : TrackedValue.Builder<B2, V> {
                @Suppress("UNCHECKED_CAST") //
                return transformer(
                        DefaultTrackedValueBuilder<V>(
                            null,
                            null,
                            sourceIndexPath,
                            contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType,
                            trackedValue,
                            valueAtTimestamp
                        )
                            as TrackedValue.Builder<B1, V>
                    )
                    .build()
                    .orElse(this)
            }
        }
    }

    override fun builder(): PlannedValue.Builder<*> {
        return DefaultPlannedValueBuilder<Any>()
    }
}
