package funcify.feature.datasource.metadata.temporal

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
interface DataSourceAttributeLastUpdatedProvider<SI : SourceIndex<SI>> {

    fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataSource: DataSource<SI>
    ): Deferred<ImmutableSet<SchematicPath>>

}
