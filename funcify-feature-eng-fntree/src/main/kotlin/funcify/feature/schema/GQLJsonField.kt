package funcify.feature.schema

import com.fasterxml.jackson.databind.JsonNode
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-04-05
 */
interface GQLJsonField {

    val fieldDefinition: GraphQLFieldDefinition

    val fieldName: String
        get() = fieldDefinition.name

    val arguments: ImmutableSet<GraphQLArgument>
        get() = fieldDefinition.arguments.toPersistentSet()

    val outputType: GraphQLOutputType
        get() = fieldDefinition.type

    fun buildFetcher(): DataFetcherBuilder

    interface DataFetcherBuilder {

        fun selectChildPath(path: String): DataFetcherBuilder

        fun selectChildPaths(paths: Iterable<String>): DataFetcherBuilder

        fun build(): ReactiveJsonDataFetcher
    }

    interface ReactiveJsonDataFetcher : (DataFetchingEnvironment, JsonNode) -> Mono<JsonNode> {

        val gqlJsonField: GQLJsonField

        val selectedChildFieldsByPath: ImmutableMap<String, GraphQLFieldDefinition>

        override fun invoke(
            environment: DataFetchingEnvironment,
            inputJson: JsonNode
        ): Mono<JsonNode>
    }
}
