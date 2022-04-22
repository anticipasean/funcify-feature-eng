package funcify.feature.datasource.graphql.schema

import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLList
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal data class DefaultGraphQLSourceContainerType(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val schemaFieldDefinition: GraphQLFieldDefinition,
    override val sourceAttributes: PersistentSet<GraphQLSourceAttribute> = persistentSetOf()
) : GraphQLSourceContainerType {

    /** Should not throw exception if created through graphql source index factory */
    override val containerType: GraphQLFieldsContainer by lazy {
        when (val gqlType: GraphQLOutputType = schemaFieldDefinition.type) {
            is GraphQLFieldsContainer -> {
                gqlType
            }
            is GraphQLList -> {
                if (gqlType.wrappedType is GraphQLFieldsContainer) {
                    gqlType.wrappedType as GraphQLFieldsContainer
                } else {
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        "graphql container type not found in schema_field_definition"
                    )
                }
            }
            else -> {
                throw GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    "graphql container type not found in schema_field_definition"
                )
            }
        }
    }

    val sourceAttributesByName: PersistentMap<String, GraphQLSourceAttribute> by lazy {
        sourceAttributes
            .parallelStream()
            .map { gqlsa -> gqlsa.name.qualifiedForm to gqlsa }
            .reducePairsToPersistentMap()
    }

    override fun getSourceAttributeWithName(name: String): GraphQLSourceAttribute? {
        return sourceAttributesByName[name]
    }
}
