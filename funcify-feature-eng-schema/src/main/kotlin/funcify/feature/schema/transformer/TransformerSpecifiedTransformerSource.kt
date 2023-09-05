package funcify.feature.schema.transformer

import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-09-05
 */
interface TransformerSpecifiedTransformerSource {

    val transformerFieldCoordinates: FieldCoordinates

    val transformerPath: GQLOperationPath

    val transformerFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val argumentsByName: ImmutableMap<String, GraphQLArgument>

    val argumentPathsByName: ImmutableMap<String, GQLOperationPath>

    val argumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val argumentsWithoutDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val argumentsWithoutDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val transformerSource: TransformerSource

    interface Builder {

        fun transformerFieldCoordinates(transformerFieldCoordinates: FieldCoordinates): Builder

        fun transformerPath(transformerPath: GQLOperationPath): Builder

        fun transformerFieldDefinition(transformerFieldDefinition: GraphQLFieldDefinition): Builder

        fun transformerSource(transformerSource: TransformerSource): Builder

        fun build(): TransformerSpecifiedTransformerSource
    }
}
