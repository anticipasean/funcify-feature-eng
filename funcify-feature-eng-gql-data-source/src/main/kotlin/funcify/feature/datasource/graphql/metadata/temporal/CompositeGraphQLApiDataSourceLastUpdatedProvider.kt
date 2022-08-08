package funcify.feature.datasource.graphql.metadata.temporal

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
internal class CompositeGraphQLApiDataSourceLastUpdatedProvider(
    private val lastUpdatedAttributeProviders:
        List<GraphQLApiDataSourceLastUpdatedAttributeProvider>
) : GraphQLApiDataSourceLastUpdatedAttributeProvider {

    companion object {
        private val logger: Logger = loggerFor<CompositeGraphQLApiDataSourceLastUpdatedProvider>()
    }

    override fun provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
        dataSource: DataSource<GraphQLSourceIndex>
    ): Deferred<ImmutableSet<SchematicPath>> {
        logger.info(
            """provide_temporal_attribute_paths_in_datasource_for_use_in_last_updated_calculations: 
            |[ datasource.name: ${dataSource.name} 
            |]""".flatten()
        )
        return Deferred.deferredIterable(
                lastUpdatedAttributeProviders.fold(
                    persistentListOf<Deferred<ImmutableSet<SchematicPath>>>()
                ) { pl, provider ->
                    pl.add(
                        provider
                            .provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
                                dataSource
                            )
                    )
                }
            )
            .let { d: Deferred<ImmutableSet<SchematicPath>> ->
                Deferred.fromMono(
                    d.toFlux().reduce(persistentSetOf()) { accSet, set -> accSet.addAll(set) }
                )
            }
    }
}
