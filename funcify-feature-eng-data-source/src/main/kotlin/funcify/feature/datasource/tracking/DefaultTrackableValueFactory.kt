package funcify.feature.datasource.tracking

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.tracking.TrackableValue.CalculatedValue
import funcify.feature.datasource.tracking.TrackableValue.PlannedValue
import funcify.feature.datasource.tracking.TrackableValue.TrackedValue
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
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

        internal class DefaultPlannedValueBuilder<V>(
            private var sourceIndexPath: SchematicPath? = null,
            private var contextualParameters: PersistentMap.Builder<SchematicPath, JsonNode> =
                persistentMapOf<SchematicPath, JsonNode>().builder()
        ) : PlannedValue.Builder<V> {

            override fun sourceIndexPath(sourceIndexPath: SchematicPath): PlannedValue.Builder<V> {
                this.sourceIndexPath = sourceIndexPath
                return this
            }

            override fun contextualParameters(
                contextualParameters: ImmutableMap<SchematicPath, JsonNode>
            ): PlannedValue.Builder<V> {
                this.contextualParameters = contextualParameters.toPersistentMap().builder()
                return this
            }

            override fun addContextualParameter(
                parameterPath: SchematicPath,
                parameterValue: JsonNode
            ): PlannedValue.Builder<V> {
                this.contextualParameters.put(parameterPath, parameterValue)
                return this
            }

            override fun addContextualParameter(
                parameterPathValuePair: Pair<SchematicPath, JsonNode>
            ): PlannedValue.Builder<V> {
                this.contextualParameters.put(
                    parameterPathValuePair.first,
                    parameterPathValuePair.second
                )
                return this
            }

            override fun removeContextualParameter(
                parameterPath: SchematicPath
            ): PlannedValue.Builder<V> {
                this.contextualParameters.remove(parameterPath)
                return this
            }

            override fun clearContextualParameters(): PlannedValue.Builder<V> {
                this.contextualParameters.clear()
                return this
            }

            override fun addContextualParameters(
                contextualParameters: Map<SchematicPath, JsonNode>
            ): PlannedValue.Builder<V> {
                this.contextualParameters.putAll(contextualParameters)
                return this
            }

            override fun build(): Try<PlannedValue<V>> {
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
                    else -> {
                        DefaultPlannedValue<V>(
                                sourceIndexPath = sourceIndexPath!!,
                                contextualParameters = contextualParameters.build()
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultCalculatedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>,
            private var calculatedValue: V? = null,
            private var calculatedTimestamp: Instant? = null,
        ) : CalculatedValue.Builder<V> {

            override fun calculatedValue(calculatedValue: V): CalculatedValue.Builder<V> {
                this.calculatedValue = calculatedValue
                return this
            }

            override fun calculatedTimestamp(
                calculatedTimestamp: Instant
            ): CalculatedValue.Builder<V> {
                this.calculatedTimestamp = calculatedTimestamp
                return this
            }

            override fun build(): TrackableValue<V> {
                return when {
                    calculatedValue == null -> {
                        existingPlannedValue
                    }
                    calculatedTimestamp == null -> {
                        existingPlannedValue
                    }
                    else -> {
                        DefaultCalculatedValue<V>(
                            existingPlannedValue.sourceIndexPath,
                            existingPlannedValue.contextualParameters,
                            calculatedValue!!,
                            calculatedTimestamp!!
                        )
                    }
                }
            }
        }

        internal class DefaultTrackedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null,
            private val existingCalculatedValue: CalculatedValue<V>? = null,
            private var trackedValue: V? = null,
            private var valueAtTimestamp: Instant? = null,
        ) : TrackedValue.Builder<V> {

            override fun trackedValue(trackedValue: V): TrackedValue.Builder<V> {
                this.trackedValue = trackedValue
                return this
            }

            override fun valueAtTimestamp(valueAtTimestamp: Instant): TrackedValue.Builder<V> {
                this.valueAtTimestamp = valueAtTimestamp
                return this
            }

            override fun build(): TrackableValue<V> {
                return when {
                    existingPlannedValue == null && existingCalculatedValue == null -> {
                        throw IllegalStateException(
                            """either a planned value or a calculated value must exist 
                            |and be provided before use of the tracked_value_builder""".flatten()
                        )
                    }
                    trackedValue == null || valueAtTimestamp == null -> {
                        when (existingPlannedValue) {
                            null -> existingCalculatedValue!!
                            else -> existingPlannedValue
                        }
                    }
                    else -> {
                        when (existingPlannedValue) {
                            null -> {
                                DefaultTrackedValue<V>(
                                    existingCalculatedValue!!.sourceIndexPath,
                                    existingCalculatedValue.contextualParameters,
                                    trackedValue!!,
                                    valueAtTimestamp!!
                                )
                            }
                            else -> {
                                DefaultTrackedValue<V>(
                                    existingPlannedValue.sourceIndexPath,
                                    existingPlannedValue.contextualParameters,
                                    trackedValue!!,
                                    valueAtTimestamp!!
                                )
                            }
                        }
                    }
                }
            }
        }

        internal data class DefaultPlannedValue<V>(
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>
        ) : PlannedValue<V> {

            override fun <R> transitionToCalculated(
                mapper: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<R>
            ): TrackableValue<R> {
                val builder: CalculatedValue.Builder<V> = DefaultCalculatedValueBuilder<V>(this)
                return mapper(builder).build()
            }

            override fun <R> transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<R>
            ): TrackableValue<R> {
                val builder: TrackedValue.Builder<V> = DefaultTrackedValueBuilder<V>(this)
                return mapper(builder).build()
            }
        }

        internal data class DefaultCalculatedValue<V>(
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>,
            override val calculatedValue: V,
            override val calculatedTimestamp: Instant
        ) : CalculatedValue<V> {

            override fun <R> transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<R>
            ): TrackableValue<R> {
                val builder: TrackedValue.Builder<V> = DefaultTrackedValueBuilder<V>(null, this)
                return mapper(builder).build()
            }
        }

        internal data class DefaultTrackedValue<V>(
            override val sourceIndexPath: SchematicPath,
            override val contextualParameters: ImmutableMap<SchematicPath, JsonNode>,
            override val trackedValue: V,
            override val valueAtTimestamp: Instant
        ) : TrackedValue<V>
    }

    override fun <V> builder(): PlannedValue.Builder<V> {
        return DefaultPlannedValueBuilder<V>()
    }
}
