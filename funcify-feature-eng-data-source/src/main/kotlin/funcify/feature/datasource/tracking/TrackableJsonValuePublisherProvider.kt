package funcify.feature.datasource.tracking

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.DataElementSource

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
fun interface TrackableJsonValuePublisherProvider {

    companion object {
        val NO_OP_PROVIDER: TrackableJsonValuePublisherProvider =
            TrackableJsonValuePublisherProvider {
                none()
            }
    }

    fun canPublishTrackableValuesForDataSource(dataSourceKey: DataElementSource.Key<*>): Boolean {
        return getTrackableJsonValuePublisherForDataSource(dataSourceKey).isDefined()
    }

    fun getTrackableJsonValuePublisherForDataSource(
        dataSourceKey: DataElementSource.Key<*>
    ): Option<TrackableJsonValuePublisher>
}
