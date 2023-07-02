package funcify.feature.schema.directive.identifier

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-09-16
 */
fun interface DataSourceEntityIdentifiersProvider {

    fun provideEntityIdentifierSourceAttributePathsInDataSource(
        dataElementSource: DataElementSource
    ): Mono<ImmutableSet<SchematicPath>>

}
