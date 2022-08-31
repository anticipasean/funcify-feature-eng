package funcify.feature.datasource.graphql.metadata.alias

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-21
 */
internal class CompositeGraphQLApiDataSourceAliasProvider(
    private val graphQLApiDataSourceAliasProviders: List<GraphQLApiDataSourceAliasProvider>
) : GraphQLApiDataSourceAliasProvider {

    companion object {
        private val logger: Logger = loggerFor<CompositeGraphQLApiDataSourceAliasProvider>()
    }

    override fun provideAnyAliasesForAttributePathsInDataSource(
        dataSource: DataSource<GraphQLSourceIndex>
    ): KFuture<ImmutableMap<SchematicPath, ImmutableSet<String>>> {
        logger.debug(
            "provide_any_aliases_for_attribute_paths_in_datasource: [ datasource.name: ${dataSource.name} ]"
        )
        return KFuture.combineIterableOf(
                graphQLApiDataSourceAliasProviders.fold(
                    persistentListOf<KFuture<ImmutableMap<SchematicPath, ImmutableSet<String>>>>()
                ) { pl, provider ->
                    pl.add(provider.provideAnyAliasesForAttributePathsInDataSource(dataSource))
                }
            )
            .let { d ->
                d.map { il ->
                    il.fold(persistentMapOf<SchematicPath, PersistentSet<String>>()) { pm, im ->
                        im.asSequence().fold(pm) { accMap, (k, vSet) ->
                            accMap.put(k, accMap.getOrDefault(k, persistentSetOf()).addAll(vSet))
                        }
                    }
                }
            }
    }
}
