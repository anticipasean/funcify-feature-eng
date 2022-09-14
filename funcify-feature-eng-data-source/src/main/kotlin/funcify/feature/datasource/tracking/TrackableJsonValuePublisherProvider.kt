package funcify.feature.datasource.tracking

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.DataSource

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

    fun canPublishTrackableValuesForDataSource(dataSourceKey: DataSource.Key<*>): Boolean {
        return getTrackableJsonValuePublisherForDataSource(dataSourceKey).isDefined()
    }

    fun getTrackableJsonValuePublisherForDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Option<TrackableJsonValuePublisher>
}
