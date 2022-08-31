package funcify.feature.datasource.retrieval

import arrow.core.Either
import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.async.KFuture
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleSourceIndexJsonOptionCacheRetrievalFunction :
    (ImmutableMap<SchematicPath, JsonNode>) -> KFuture<Option<JsonNode>> {

    val cacheForDataSourceKey: DataSource.Key<*>
        get() = cacheForDataSource.key

    val cacheForDataSource: DataSource<*>

    val sourceIndexPath: SchematicPath

    override fun invoke(
        contextParameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>
    ): KFuture<Option<JsonNode>>

    interface Builder {

        fun cacheForDataSource(dataSource: DataSource<*>): Builder

        fun sourceTarget(
            sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
        ): Builder

        fun sourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder

        fun sourceTarget(sourceLeafVertex: SourceLeafVertex): Builder

        fun build(): Try<SingleSourceIndexJsonOptionCacheRetrievalFunction>
    }
}
