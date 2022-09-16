package funcify.feature.schema.directive.entity

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-09-16
 */
fun interface DataSourceEntityIdentifiersProvider<SI : SourceIndex<SI>> {

    fun provideEntityIdentifierSourceAttributePathsInDataSource(
        dataSource: DataSource<SI>
    ): Mono<ImmutableSet<SchematicPath>>

}
