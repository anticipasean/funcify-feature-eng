package funcify.feature.schema.dataelementsource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.tracking.TrackableValue
import reactor.core.publisher.Mono

/**
 * Strategy for retrieval of a single value for a single [SchematicPath] from tracked value storage
 * acting on behalf of a representative [DataElementSource]
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface FeatureJsonValueRetrievalStrategy<SI : SourceIndex<SI>> : FeatureJsonValueStore {

    override val cacheForDataSource: DataElementSource<*>

    override fun invoke(trackableValue: TrackableValue<JsonNode>): Mono<TrackableValue<JsonNode>>
}
