package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLInputFieldsContainer
import graphql.schema.GraphQLInputType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterInputObjectContainerType(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataType: GraphQLInputType,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val parameterAttributes: PersistentSet<GraphQLParameterAttribute> = persistentSetOf(),
) : GraphQLParameterContainerType {

    override val graphQLInputFieldsContainerType: Option<GraphQLInputFieldsContainer> by lazy {
        GraphQLInputFieldsContainerTypeExtractor.invoke(dataType)
    }

    init {
        if (sourcePath.arguments.isEmpty() && sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must have at least one argument or directive 
                   |in order to represent a container_type value 
                   |on an argument or directive: [ actual: $sourcePath ]
                   |""".flattenIntoOneLine()
            )
        }
        if (!graphQLInputFieldsContainerType.isDefined()) {
            val dataTypeSuperTypesSetStr: String =
                dataType::class
                    .supertypes
                    .asSequence()
                    .map { kt -> kt.toString() }
                    .joinToString(", ", "{ ", " }")
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """data_type given for parameter_input_object_container_type 
                    |must be a graphql_input_fields_container: [ actual: ${dataType::class.qualifiedName} 
                    |implementing $dataTypeSuperTypesSetStr ]
                    |""".flattenIntoOneLine()
            )
        }
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
