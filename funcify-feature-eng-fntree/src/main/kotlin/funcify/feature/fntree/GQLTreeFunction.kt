package funcify.feature.fntree

import com.fasterxml.jackson.databind.JsonNode
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-04-05
 */
interface GQLTreeFunction : (JsonNode) -> Mono<JsonNode> {

    val fieldName: String

    val inputArgumentsByName: ImmutableMap<String, GraphQLArgument>

    val outputType: GraphQLOutputType

    override fun invoke(jsonNode: JsonNode): Mono<JsonNode>

}
