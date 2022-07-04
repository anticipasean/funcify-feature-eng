package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterDirectiveContainerType(
    override val sourcePath: SchematicPath,
    override val graphQLAppliedDirective: Option<GraphQLAppliedDirective>,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val parameterAttributes: PersistentSet<GraphQLParameterAttribute> = persistentSetOf()
) : GraphQLParameterContainerType {

    override val name: ConventionalName by lazy {
        StandardNamingConventions.PASCAL_CASE.deriveName(
            graphQLAppliedDirective.orNull()!!.name + " Directive"
        )
    }

    override val dataType: GraphQLType by lazy { GraphQLTypeReference.typeRef(name.toString()) }

    private val parameterAttributesByName:
        PersistentMap<String, GraphQLParameterAttribute> by lazy {
        parameterAttributes
            .stream()
            .map { attr -> attr.name.toString() to attr }
            .reducePairsToPersistentMap()
    }

    init {
        if (sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must have at least one directive 
                    |in order to represent a parameter_directive_container_type: 
                    |[ actual: $sourcePath ]
                    |""".flattenIntoOneLine()
            )
        }
        if (!graphQLAppliedDirective.isDefined()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """graphql_applied_directive must be present 
                    |on a parameter_directive_container_type: 
                    |[ actual: $graphQLAppliedDirective ]""".flattenIntoOneLine()
            )
        }
        if (graphQLAppliedDirective.orNull()!!.name !in sourcePath.directives ||
                sourcePath.directives[graphQLAppliedDirective.orNull()!!.name]
                    .toOption()
                    .filter { jn -> !jn.isEmpty || !jn.isNull }
                    .isDefined()
        ) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """graphql_applied_directive.name must be represented 
                    |in source_path and must be empty or null 
                    |to represent container_type: 
                    |[ actual: $sourcePath ]
                    |""".flattenIntoOneLine()
            )
        }
    }

    override fun getParameterAttributeWithName(name: String): GraphQLParameterAttribute? {
        return parameterAttributesByName[name]
    }
}
