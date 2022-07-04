package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterDirectiveAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataType: GraphQLInputType,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val directive: Option<GraphQLAppliedDirective>
) : GraphQLParameterAttribute {

    init {
        if (sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must represent a parameter on a 
                   |source_index i.e. have at least one 
                   |directive declared: [ actual: ${sourcePath} ]
                   |""".flattenIntoOneLine()
            )
        }
    }
}
