package funcify.feature.schema.directive.alias

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.async.KFuture
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

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
    ): Mono<ImmutableMap<SchematicPath, ImmutableSet<String>>>
}
