package funcify.feature.datasource.tracking

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
interface TrackedValuePublisher<V> {

    fun publishTrackedValue(trackableValue: TrackableValue<V>)

}
