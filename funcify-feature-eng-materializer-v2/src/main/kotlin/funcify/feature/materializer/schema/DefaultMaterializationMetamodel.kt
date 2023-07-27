package funcify.feature.materializer.schema

import funcify.feature.schema.FeatureEngineeringModel
import graphql.schema.GraphQLSchema
import java.time.Instant

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val featureEngineeringModel: FeatureEngineeringModel,
    override val materializationGraphQLSchema: GraphQLSchema
) : MaterializationMetamodel {}
