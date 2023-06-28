package funcify.feature.datasource.graphql.metadata.identifier

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-09-16
 */
internal class CompositeGraphQLApiDataSourceEntityIdentifiersProvider(
    private val graphQLApiDataSourceEntityIdentifiersProvider:
        List<GraphQLApiDataSourceEntityIdentifiersProvider>
) : GraphQLApiDataSourceEntityIdentifiersProvider {

    companion object {
        private val logger: Logger =
            loggerFor<CompositeGraphQLApiDataSourceEntityIdentifiersProvider>()
    }

    override fun provideEntityIdentifierSourceAttributePathsInDataSource(
        dataSource: DataElementSource<GraphQLSourceIndex>
    ): Mono<ImmutableSet<SchematicPath>> {
        logger.debug(
            "provide_entity_identifier_source_attribute_paths_in_data_source: [ data_source.key.name: {} ]",
            dataSource.key.name
        )
        return Flux.merge(
                graphQLApiDataSourceEntityIdentifiersProvider.map { prov ->
                    prov.provideEntityIdentifierSourceAttributePathsInDataSource(dataSource)
                }
            )
            .reduce(persistentSetOf<SchematicPath>()) { ps, entIdSet -> ps.addAll(entIdSet) }
            .cache()
            .widen()
    }
}
