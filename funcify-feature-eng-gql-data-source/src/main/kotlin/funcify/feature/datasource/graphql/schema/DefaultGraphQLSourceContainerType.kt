package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.Some
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.GraphQLFieldsContainer
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
    override val dataType: GraphQLOutputType,
    override val sourceAttributes: PersistentSet<GraphQLSourceAttribute> = persistentSetOf()
) : GraphQLSourceContainerType {

    /** Should not throw exception if created through graphql source index factory */
    override val containerType: GraphQLFieldsContainer by lazy {
        when (val gqlFieldContOpt: Option<GraphQLFieldsContainer> =
                GraphQLFieldsContainerTypeExtractor.invoke(dataType)
        ) {
            is Some -> gqlFieldContOpt.value
            else -> {
                throw GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    "graphql_fields_container_type not found in data_type"
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
