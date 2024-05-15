package funcify.feature.schema.feature

import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-19
 */
interface FeatureSpecifiedFeatureCalculator {

    val featureFieldCoordinates: FieldCoordinates

    val featureName: String

    val featurePath: GQLOperationPath

    val featureFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val argumentsByName: ImmutableMap<String, GraphQLArgument>

    val transformerFieldCoordinates: FieldCoordinates

    val featureCalculator: FeatureCalculator

    interface Builder {

        fun featureFieldCoordinates(featureFieldCoordinates: FieldCoordinates): Builder

        fun featureName(featureName: String): Builder

        fun featurePath(featurePath: GQLOperationPath): Builder

        fun featureFieldDefinition(featureFieldDefinition: GraphQLFieldDefinition): Builder

        fun featureCalculator(featureCalculator: FeatureCalculator): Builder

        fun transformerFieldCoordinates(transformerFieldCoordinates: FieldCoordinates): Builder

        fun build(): FeatureSpecifiedFeatureCalculator
    }
}
