package funcify.feature.datasource.retrieval

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface TrackableValueJsonRetrievalFunction :
    (ImmutableMap<SchematicPath, JsonNode>) -> Mono<TrackableValue<JsonNode>> {

    val cacheForDataSourceKey: DataSource.Key<*>
        get() = cacheForDataSource.key

    val cacheForDataSource: DataSource<*>

    val sourceIndexPath: SchematicPath
        get() = sourceJunctionOrLeafVertex.fold(SourceJunctionVertex::path, SourceLeafVertex::path)

    val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>

    override fun invoke(
        contextParameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>
    ): Mono<TrackableValue<JsonNode>>

    interface Builder {

        fun cacheForDataSource(dataSource: DataSource<*>): Builder

        fun sourceTarget(
            sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
        ): Builder

        fun sourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder

        fun sourceTarget(sourceLeafVertex: SourceLeafVertex): Builder

        fun build(): Try<TrackableValueJsonRetrievalFunction>
    }
}
