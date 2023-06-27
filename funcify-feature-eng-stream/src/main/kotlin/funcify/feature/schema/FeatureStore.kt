package funcify.feature.schema

/**
 *
 * @author smccarron
 * @created 2023-06-26
 */
interface FeatureStore : DataSource {

    // Can put type variable for trackable value in class signature
    // fun retrieveTrackableValueIfAvailable(plannedValue: TrackableValue.PlannedValue<V>): Mono<TrackableValue<V>>


}
