package funcify.feature.datasource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-09-01
 */
sealed interface TrackableValue<V> {

    val sourceIndexPath: SchematicPath

    val contextualParameters: ImmutableMap<SchematicPath, JsonNode>

    fun isPlanned(): Boolean {
        return fold({ true }, { false }, { false })
    }

    fun isCalculated(): Boolean {
        return fold({ false }, { true }, { false })
    }

    fun isTracked(): Boolean {
        return fold({ false }, { false }, { true })
    }

    fun <R> fold(
        planned: (PlannedValue<V>) -> R,
        calculated: (CalculatedValue<V>) -> R,
        tracked: (TrackedValue<V>) -> R
    ): R

    interface PlannedValue<V> : TrackableValue<V> {

        fun transitionToCalculated(
            mapper: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
        ): TrackableValue<V>

        fun transitionToTracked(
            mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
        ): TrackableValue<V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return planned(this)
        }

        interface Builder<V> {

            fun sourceIndexPath(sourceIndexPath: SchematicPath): Builder<V>

            /** Replaces all current contextual_parameters with this map */
            fun contextualParameters(
                contextualParameters: ImmutableMap<SchematicPath, JsonNode>
            ): Builder<V>

            fun addContextualParameter(
                parameterPath: SchematicPath,
                parameterValue: JsonNode
            ): Builder<V>

            fun addContextualParameter(
                parameterPathValuePair: Pair<SchematicPath, JsonNode>
            ): Builder<V>

            fun removeContextualParameter(parameterPath: SchematicPath): Builder<V>

            fun clearContextualParameters(): Builder<V>

            /** Adds all contextual_parameters to the existing map */
            fun addContextualParameters(
                contextualParameters: Map<SchematicPath, JsonNode>
            ): Builder<V>

            /**
             * Success<PlannedValue<V>> if one can be built, else Failure<PlannedValue<V>> with an
             * error indicating what was missing or invalid
             */
            fun build(): Try<PlannedValue<V>>
        }
    }

    interface CalculatedValue<V> : TrackableValue<V> {

        val calculatedValue: V

        val calculatedTimestamp: Instant

        fun transitionToTracked(
            mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
        ): TrackableValue<V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return calculated(this)
        }

        interface Builder<V> {

            fun calculatedValue(calculatedValue: V): Builder<V>

            fun calculatedTimestamp(calculatedTimestamp: Instant): Builder<V>

            fun build(): TrackableValue<V>
        }
    }

    interface TrackedValue<V> : TrackableValue<V> {

        val trackedValue: V

        val valueAtTimestamp: Instant

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return tracked(this)
        }

        interface Builder<V> {

            fun trackedValue(trackedValue: V): Builder<V>

            fun valueAtTimestamp(valueAtTimestamp: Instant): Builder<V>

            fun build(): TrackableValue<V>
        }
    }
}
