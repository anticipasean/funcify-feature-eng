package funcify.feature.datasource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.schema.datasource.DataSource
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface TrackableJsonValueRetriever :
    (TrackableValue<JsonNode>) -> Mono<TrackableValue<JsonNode>> {

    val cacheForDataSourceKey: DataSource.Key<*>
        get() = cacheForDataSource.key

    val cacheForDataSource: DataSource<*>

    override fun invoke(trackableValue: TrackableValue<JsonNode>): Mono<TrackableValue<JsonNode>>

    interface Builder {

        fun cacheForDataSource(dataSource: DataSource<*>): Builder

        fun build(): Try<TrackableJsonValueRetriever>
    }
}
