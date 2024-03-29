package funcify.feature.datasource.retrieval

import arrow.core.Either
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface DataSourceRepresentativeJsonRetrievalStrategyProvider<SI : SourceIndex<SI>> {

    fun providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun createExternalDataSourceJsonValuesRetrieverFor(
        dataSource: DataSource<SI>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>
    ): Try<ExternalDataSourceJsonValuesRetriever>
}
