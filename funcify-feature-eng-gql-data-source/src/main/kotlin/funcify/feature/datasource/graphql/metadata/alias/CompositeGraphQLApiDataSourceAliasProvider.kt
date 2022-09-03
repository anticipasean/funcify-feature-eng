package funcify.feature.datasource.graphql.metadata.alias

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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
    ): Mono<ImmutableMap<SchematicPath, ImmutableSet<String>>> {
        logger.debug(
            "provide_any_aliases_for_attribute_paths_in_datasource: [ datasource.name: ${dataSource.name} ]"
        )
        return Flux.fromIterable(graphQLApiDataSourceAliasProviders)
            .flatMap { provider ->
                provider.provideAnyAliasesForAttributePathsInDataSource(dataSource)
            }
            .reduce(persistentMapOf<SchematicPath, PersistentSet<String>>()) { resultMap, aliasSet
                ->
                aliasSet.asSequence().fold(resultMap) { pm, (path, aliases) ->
                    pm.put(path, pm.getOrDefault(path, persistentSetOf()).addAll(aliases))
                }
            }
            .widen()
    }
}
