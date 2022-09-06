package funcify.feature.datasource.tracking

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.DataSource

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
fun interface TrackedJsonValuePublisherProvider {

    companion object {
        val NO_OP_PROVIDER: TrackedJsonValuePublisherProvider = TrackedJsonValuePublisherProvider {
            none()
        }
    }

    fun canPublishTrackedValuesForDataSource(dataSourceKey: DataSource.Key<*>): Boolean {
        return getTrackedValuePublisherForDataSource(dataSourceKey).isDefined()
    }

    fun getTrackedValuePublisherForDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Option<TrackedJsonValuePublisher>
}
