package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterArgumentAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataType: GraphQLInputType,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val argument: Option<GraphQLArgument>
) : GraphQLParameterAttribute {

    init {
        if (sourcePath.arguments.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must represent an argument parameter on a 
                   |source_index i.e. have at least one argument 
                   |declared: [ actual: ${sourcePath} ]
                   |""".flattenIntoOneLine()
            )
        }
    }
}
