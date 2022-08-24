package funcify.feature.datasource.retrieval

import arrow.core.Either
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface DataSourceCacheJsonRetrievalStrategyProvider<SI : SourceIndex<SI>> {

    fun providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun createSingleSourceIndexJsonOptionRetrievalFunctionForCacheFor(
        dataSource: DataSource<SI>,
        sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
    ): Try<SingleSourceIndexJsonOptionCacheRetrievalFunction>
}
