package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLFieldDefinition

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal data class DefaultGraphQLSourceAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val schemaFieldDefinition: GraphQLFieldDefinition
) : GraphQLSourceAttribute {}
