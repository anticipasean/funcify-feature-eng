package funcify.feature.materializer.model

import funcify.feature.schema.feature.FeatureCalculator
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

    val fieldCoordinates: FieldCoordinates

    val featureName: String

    val featurePath: GQLOperationPath

    val featureFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val argumentsByName: ImmutableMap<String, GraphQLArgument>

    val featureCalculator: FeatureCalculator

    interface Builder {

        fun fieldCoordinates(fieldCoordinates: FieldCoordinates): Builder

        fun featureName(featureName: String): Builder

        fun featurePath(featurePath: GQLOperationPath): Builder

        fun featureFieldDefinition(featureFieldDefinition: GraphQLFieldDefinition): Builder

        fun putArgumentForPath(path: GQLOperationPath, argument: GraphQLArgument): Builder

        fun putAllPathArguments(pathArgumentPairs: Map<GQLOperationPath, GraphQLArgument>): Builder

        fun putArgumentForName(name: String, argument: GraphQLArgument): Builder

        fun putAllNameArguments(nameArgumentPairs: Map<String, GraphQLArgument>): Builder

        fun featureCalculator(featureCalculator: FeatureCalculator): Builder

        fun build(): FeatureSpecifiedFeatureCalculator
    }
}
