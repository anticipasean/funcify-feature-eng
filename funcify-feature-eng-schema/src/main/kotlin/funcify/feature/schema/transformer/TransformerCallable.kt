package funcify.feature.schema.transformer

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface TransformerCallable : (ImmutableMap<String, JsonNode>) -> Mono<out JsonNode> {

    val transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource

    val transformerFieldCoordinates: FieldCoordinates
        get() = transformerSpecifiedTransformerSource.transformerFieldCoordinates

    val transformerPath: GQLOperationPath
        get() = transformerSpecifiedTransformerSource.transformerPath

    val transformerGraphQLFieldDefinition: GraphQLFieldDefinition
        get() = transformerSpecifiedTransformerSource.transformerFieldDefinition

    val argumentsByName: ImmutableMap<String, GraphQLArgument>
        get() = transformerSpecifiedTransformerSource.argumentsByName

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>
        get() = transformerSpecifiedTransformerSource.argumentsByPath

    val defaultArgumentValuesByName: ImmutableMap<String, JsonNode>
        get() = transformerSpecifiedTransformerSource.defaultArgumentValuesByName

    override fun invoke(arguments: ImmutableMap<String, JsonNode>): Mono<out JsonNode>

    interface Builder {

        fun selectTransformer(
            transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource
        ): Builder

        fun build(): TransformerCallable
    }
}
