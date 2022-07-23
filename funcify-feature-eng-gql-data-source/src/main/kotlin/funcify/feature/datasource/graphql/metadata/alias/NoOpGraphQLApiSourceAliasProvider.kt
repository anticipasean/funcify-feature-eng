package funcify.feature.datasource.graphql.metadata.alias

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-21
 */
internal class NoOpGraphQLApiSourceAliasProvider : GraphQLApiSourceAliasProvider {

    companion object {
        private val logger: Logger = loggerFor<NoOpGraphQLApiSourceAliasProvider>()
    }

    override fun provideAnyAliasesForAttributePathsInDataSource(
        dataSource: DataSource<GraphQLSourceIndex>
    ): Deferred<ImmutableMap<SchematicPath, ImmutableSet<String>>> {
        val graphQLApiDataSource: GraphQLApiDataSource =
            dataSource as? GraphQLApiDataSource
                ?: throw GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    """unhandled type of graphql_data_source provided: 
                    |[ actual: ${dataSource::class.qualifiedName} 
                    |]""".flattenIntoOneLine()
                )
        val sourceMetamodel = graphQLApiDataSource.sourceMetamodel as GraphQLSourceMetamodel
        logger.debug(
            """get_aliases_for_attribute_paths: 
            |[ source_metamodel.source_indices_by_path.size: 
            |${sourceMetamodel.sourceIndicesByPath.size} ,
            |source_metamodel.source_indices_by_path.first.key: 
            |${sourceMetamodel.sourceIndicesByPath.asSequence().map { (p, _) -> p }.firstOrNull()}
            |]""".flattenIntoOneLine()
        )
       return Deferred.completed(persistentMapOf())
    }
}
