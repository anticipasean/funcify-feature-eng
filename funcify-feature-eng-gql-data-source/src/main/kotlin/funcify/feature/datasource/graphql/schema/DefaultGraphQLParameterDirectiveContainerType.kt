package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLInputFieldsContainer
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
    override val dataSourceLookupKey: DataElementSource.Key<GraphQLSourceIndex>,
    override val directive: Option<GraphQLAppliedDirective>,
    override val parameterAttributes: PersistentSet<GraphQLParameterAttribute> = persistentSetOf()
) : GraphQLParameterContainerType {

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
            .map { dir ->
                StandardNamingConventions.PASCAL_CASE.deriveName(dir.name + "_Directive")
            }
            .successIfDefined()
            .orElseThrow()
    }

    override val inputFieldsContainerType: GraphQLInputFieldsContainer
        get() = dataType as GraphQLInputFieldsContainer

    private val parameterAttributesByName:
        PersistentMap<String, GraphQLParameterAttribute> by lazy {
        parameterAttributes
            .stream()
            .map { attr -> attr.name.toString() to attr }
            .reducePairsToPersistentMap()
    }

    override fun getParameterAttributeWithName(name: String): GraphQLParameterAttribute? {
        return parameterAttributesByName[name]
    }
}
