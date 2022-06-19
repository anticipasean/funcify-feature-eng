package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal data class DefaultGraphQLSourceAttribute(
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val schemaFieldDefinition: GraphQLFieldDefinition
) : GraphQLSourceAttribute {

    override val arguments: ImmutableList<GraphQLArgument> by lazy {
        schemaFieldDefinition.arguments.toPersistentList()
    }
}
