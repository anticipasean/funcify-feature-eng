package funcify.feature.schema.directive.temporal

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
interface DataSourceAttributeLastUpdatedProvider<SI : SourceIndex<SI>> {

    fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataSource: DataSource<SI>
    ): Mono<ImmutableSet<SchematicPath>>
}
