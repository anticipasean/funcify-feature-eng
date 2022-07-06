package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterDirectiveArgumentAttribute(
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val directiveArgument: Option<GraphQLAppliedDirectiveArgument>
) : GraphQLParameterAttribute {

    init {
        directiveArgument
            .successIfDefined {
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    "applied_directive_argument must be defined for this type"
                )
            }
            .orElseThrow()
    }

    override val dataType: GraphQLInputType by lazy {
        directiveArgument.map { arg -> arg.type }.successIfDefined().orElseThrow()
    }
}
