package funcify.feature.schema.tracking

/**
 * @author smccarron
 * @created 2022-09-05
 */
interface TrackableValueFactory {

    fun builder(): TrackableValue.PlannedValue.Builder
}
