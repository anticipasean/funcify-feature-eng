package funcify.feature.schema.directive.temporal

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
fun interface DataSourceAttributeLastUpdatedProvider<SI : SourceIndex<SI>> {

    fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataSource: DataElementSource<SI>
    ): Mono<ImmutableSet<SchematicPath>>
}
