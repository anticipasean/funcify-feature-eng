package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterInputObjectFieldAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val inputObjectField: Option<GraphQLInputObjectField>
) : GraphQLParameterAttribute {

    init {
        inputObjectField
            .successIfDefined {
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    """input_object_field must be defined 
                       |in 
                       |${DefaultGraphQLParameterInputObjectFieldAttribute::class.qualifiedName}
                       |""".flattenIntoOneLine()
                )
            }
            .orElseThrow()
    }

    override val dataType: GraphQLInputType by lazy {
        inputObjectField.map { iof -> iof.type }.successIfDefined().orElseThrow()
    }
}
