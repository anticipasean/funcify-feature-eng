package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLArgument

internal data class DefaultGraphQLFilterArgumentEdge(
    override val id: Pair<SchematicPath, SchematicPath>,
    override val argument: GraphQLArgument,
) : GraphQLFilterArgumentEdge {}
