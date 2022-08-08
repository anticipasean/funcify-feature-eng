package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterDirectiveAttribute(
    override val sourcePath: SchematicPath,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val directive: Option<GraphQLAppliedDirective>
) : GraphQLParameterAttribute {

    init {
        directive
            .successIfDefined {
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    "directive must be defined for this type"
                )
            }
            .orElseThrow()
        if (sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must represent a parameter on a 
                   |source_index i.e. have at least one 
                   |directive declared: [ actual: ${sourcePath} ]
                   |""".flatten()
            )
        }
    }

    override val name: ConventionalName by lazy {
        directive
            .map { dir -> dir.name }
            .map { n -> StandardNamingConventions.IDENTITY.deriveName(n) }
            .successIfDefined()
            .orElseThrow()
    }

    /**
     * Pseudotype since directives act as both a container_type and attribute but do not have
     * "field_definitions" like an output object_type or an input_object_type, but also do not have
     * a scalar type in the same way output "field_definitions" or "input_field_definitions" can
     */
    override val dataType: GraphQLInputType by lazy {
        directive
            .map { gd -> gd.name }
            .map { n ->
                StandardNamingConventions.PASCAL_CASE.deriveName(n + "_Directive").toString()
            }
            .map { typename -> GraphQLInputObjectType.newInputObject().name(typename).build() }
            .successIfDefined()
            .orElseThrow()
    }
}
