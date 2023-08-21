package funcify.feature.materializer.schema

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2022-08-04
 */
interface MaterializationMetamodel {

    val created: Instant

    val featureEngineeringModel: FeatureEngineeringModel

    val materializationGraphQLSchema: GraphQLSchema

    val querySchemaElementsByCanonicalPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByCanonicalPath: ImmutableMap<GQLOperationPath, FieldCoordinates>

    val canonicalPathsByFieldCoordinates: ImmutableMap<FieldCoordinates, GQLOperationPath>

    val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>

    val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>

    val featurePathsByName: ImmutableMap<String, GQLOperationPath>
}
