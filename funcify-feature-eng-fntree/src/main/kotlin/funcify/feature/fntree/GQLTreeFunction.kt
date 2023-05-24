package funcify.feature.fntree

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2023-04-05
 */
interface GQLTreeFunction {

    val fieldDefinition: GraphQLFieldDefinition

    val inputArgumentsByName: ImmutableMap<String, GraphQLArgument>

    val outputType: GraphQLOutputType
}
