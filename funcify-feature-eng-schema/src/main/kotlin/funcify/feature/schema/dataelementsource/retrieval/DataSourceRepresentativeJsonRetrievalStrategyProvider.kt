package funcify.feature.schema.dataelementsource.retrieval

import arrow.core.Either
import funcify.feature.schema.dataelementsource.DataElementSource
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
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean

    fun createExternalDataSourceJsonValuesRetrieverFor(
        dataSource: DataElementSource<SI>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>
    ): Try<DataElementJsonValueSource>
}
