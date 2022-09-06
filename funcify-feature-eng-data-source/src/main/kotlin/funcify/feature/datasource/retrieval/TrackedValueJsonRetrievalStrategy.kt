package funcify.feature.datasource.retrieval

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * Strategy for retrieval of a single value for a single [SchematicPath] from tracked value storage
 * acting on behalf of a representative [DataSource]
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface TrackedValueJsonRetrievalStrategy<SI : SourceIndex<SI>> :
    TrackableValueJsonRetrievalFunction {

    override val cacheForDataSource: DataSource<*>

    override val sourceIndexPath: SchematicPath

    override val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>

    override fun invoke(
        contextParameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>
    ): Mono<TrackableValue<JsonNode>>
}
