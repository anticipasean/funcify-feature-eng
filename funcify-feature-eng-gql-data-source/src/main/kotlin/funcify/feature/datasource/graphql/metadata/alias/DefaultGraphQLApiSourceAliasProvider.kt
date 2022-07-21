package funcify.feature.datasource.graphql.metadata.alias

import funcify.feature.datasource.graphql.schema.GraphQLParameterContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.directive.AliasDirective
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-21
 */
internal class DefaultGraphQLApiSourceAliasProvider : GraphQLApiSourceAliasProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLApiSourceAliasProvider>()
    }

    override fun getAliasesForAttributePaths(
        sourceMetamodel: SourceMetamodel<GraphQLSourceIndex>
    ): ImmutableMap<SchematicPath, String> {
        logger.debug(
            """get_aliases_for_attribute_paths: 
            |[ source_metamodel.source_indices_by_path.size: 
            |${sourceMetamodel.sourceIndicesByPath.size} ,
            |source_metamodel.source_indices_by_path.first.key: 
            |${sourceMetamodel.sourceIndicesByPath.asSequence().map { (p, _) -> p }.firstOrNull()}
            |]""".flattenIntoOneLine()
        )
        sourceMetamodel.sourceIndicesByPath.asSequence().filter { (sp, siSet) ->
            siSet.any { si ->
                si is GraphQLParameterContainerType &&
                    si.directive
                        .filter { d ->
                            d.name.startsWith(prefix = AliasDirective.name, ignoreCase = true)
                        }
                        .isDefined()
            }
        }
        TODO("finish alias gathering and aggregating")
    }
}
