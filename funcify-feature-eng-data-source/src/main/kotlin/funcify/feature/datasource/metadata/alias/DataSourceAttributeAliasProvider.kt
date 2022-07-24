package funcify.feature.datasource.metadata.alias

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * Provide alternate names for the source indices referenced by specific paths in the
 * [SourceMetamodel]
 *
 * @author smccarron
 * @created 2022-07-21
 */
fun interface DataSourceAttributeAliasProvider<SI : SourceIndex<SI>> {

    fun provideAnyAliasesForAttributePathsInDataSource(
        dataSource: DataSource<SI>
    ): Deferred<ImmutableMap<SchematicPath, ImmutableSet<String>>>
}
