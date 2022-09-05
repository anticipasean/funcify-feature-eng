package funcify.feature.datasource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-09-01
 */
interface TrackableValue<V> {

    val sourceIndexPath: SchematicPath

    val contextualParameters: ImmutableMap<SchematicPath, JsonNode>

    fun isPlanned(): Boolean

    fun isCalculated(): Boolean

    fun isTracked(): Boolean

    fun <R> fold(
        planned: (PlannedValue<V>) -> R,
        calculated: (CalculatedValue<V>) -> R,
        tracked: (TrackedValue<V>) -> R
    ): R

    interface PlannedValue<V> : TrackableValue<V> {

    }

    interface CalculatedValue<V> : TrackableValue<V> {

        val calculatedValue: V

        val calculatedTimestamp: Instant

        fun addToTrackingQueue(
            calculatedValueConsumer: (CalculatedValue<V>) -> Unit
        ): TrackedValue<V>
    }

    interface TrackedValue<V> : TrackableValue<V> {

        val trackedValue: V

        val valueAtTimestamp: Instant
    }
}
