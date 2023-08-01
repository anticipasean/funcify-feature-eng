package funcify.feature.schema.directive.temporal

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.GQLOperationPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-07-24
 */
fun interface DataSourceAttributeLastUpdatedProvider {

    fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataElementSource: DataElementSource
    ): Mono<ImmutableSet<GQLOperationPath>>
}
