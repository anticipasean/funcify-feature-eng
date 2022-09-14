package funcify.feature.datasource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import reactor.core.publisher.Mono

/**
 * Strategy for retrieval of a single value for a single [SchematicPath] from tracked value storage
 * acting on behalf of a representative [DataSource]
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface TrackedValueJsonRetrievalStrategy<SI : SourceIndex<SI>> : TrackableJsonValueRetriever {

    override val cacheForDataSource: DataSource<*>

    override fun invoke(trackableValue: TrackableValue<JsonNode>): Mono<TrackableValue<JsonNode>>
}
