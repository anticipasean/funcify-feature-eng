package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputFieldsContainer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterFieldArgumentContainerType(
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val fieldArgument: Option<GraphQLArgument>,
    override val parameterAttributes: PersistentSet<GraphQLParameterAttribute> = persistentSetOf()
) : GraphQLParameterContainerType {

    init {
        fieldArgument
            .successIfDefined {
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    "field_argument must be defined for this type"
                )
            }
            .orElseThrow()
    }

    override val inputFieldsContainerType: GraphQLInputFieldsContainer by lazy {
        fieldArgument
            .flatMap { arg -> GraphQLInputFieldsContainerTypeExtractor.invoke(arg.type) }
            .successIfDefined {
                val actualInputType = fieldArgument.map(GraphQLArgument::getType).orNull()
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.INVALID_INPUT,
                    """field_argument.type must be a 
                       |graphql_input_fields_container type: 
                       |[ actual: $actualInputType}
                       |]""".flatten()
                )
            }
            .orElseThrow()
    }

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
