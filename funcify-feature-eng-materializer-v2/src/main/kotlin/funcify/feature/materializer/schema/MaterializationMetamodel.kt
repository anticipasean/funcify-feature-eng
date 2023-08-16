package funcify.feature.materializer.schema

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.time.Instant

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

    val domainSpecifiedDataElementSourceByPath: ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>

}
