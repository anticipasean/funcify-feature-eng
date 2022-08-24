package funcify.feature.datasource.retrieval

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap

/**
 * Strategy for retrieval of a single value for a single [SchematicPath] from a cache acting on
 * behalf of a representative [DataSource]
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface DataSourceCacheJsonRetrievalStrategy<SI : SourceIndex<SI>> :
    SingleSourceIndexJsonOptionCacheRetrievalFunction {

    override val cacheForDataSource: DataSource<*>

    override val sourceIndexPath: SchematicPath

    override fun invoke(
        contextParameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<Option<JsonNode>>
}
