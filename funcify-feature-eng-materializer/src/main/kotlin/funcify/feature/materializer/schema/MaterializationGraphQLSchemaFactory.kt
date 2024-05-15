package funcify.feature.materializer.schema

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLSchema

interface MaterializationGraphQLSchemaFactory {

    fun createGraphQLSchemaFromMetamodel(
        featureEngineeringModel: FeatureEngineeringModel
    ): Try<GraphQLSchema>
}
