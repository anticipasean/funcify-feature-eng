package funcify.feature.datasource.graphql.metadata.identifier

import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.toOption
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import graphql.Scalars
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 *
 * @author smccarron
 * @created 2022-09-16
 */
class GraphQLIdScalarTypeEntityIdentifiersProvider : GraphQLApiDataSourceEntityIdentifiersProvider {

    companion object {
        private val logger: Logger = loggerFor<GraphQLIdScalarTypeEntityIdentifiersProvider>()
    }

    override fun provideEntityIdentifierSourceAttributePathsInDataSource(
        dataSource: DataSource<GraphQLSourceIndex>
    ): Mono<ImmutableSet<SchematicPath>> {
        logger.debug(
            "provide_entity_identifier_source_attribute_paths_in_data_source: [ datasource.key.name: {} ]",
            dataSource.key.name
        )
        return dataSource
            .toOption()
            .filterIsInstance<GraphQLSourceMetamodel>()
            .map { metamodel -> metamodel.sourceIndicesByPath.asSequence() }
            .fold(::emptySequence, ::identity)
            .flatMap { (sourcePath, srcIndices) ->
                srcIndices
                    .asSequence()
                    .filterIsInstance<GraphQLSourceAttribute>()
                    .filter { gqlSa -> gqlSa.dataType == Scalars.GraphQLID }
                    .map { gqlSa -> sourcePath }
            }
            .fold(persistentSetOf<SchematicPath>()) { ps, sp -> ps.add(sp) }
            .toMono()
            .cache()
            .widen()
    }
}
