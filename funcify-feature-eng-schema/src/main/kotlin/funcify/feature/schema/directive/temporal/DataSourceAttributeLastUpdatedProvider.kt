package funcify.feature.schema.directive.temporal

import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
fun interface DataSourceAttributeLastUpdatedProvider {

    fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataElementSource: DataElementSource
    ): Mono<ImmutableSet<SchematicPath>>
}
