package funcify.feature.materializer.model

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.time.Instant

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val featureEngineeringModel: FeatureEngineeringModel,
    override val materializationGraphQLSchema: GraphQLSchema,
    override val childCanonicalPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>,
    override val querySchemaElementsByCanonicalPath:
        ImmutableMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByCanonicalPath: ImmutableMap<GQLOperationPath, FieldCoordinates>,
    override val canonicalPathsByFieldCoordinates: ImmutableMap<FieldCoordinates, GQLOperationPath>,
    override val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>,
    override val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>,
    override val featurePathsByName: ImmutableMap<String, GQLOperationPath>,
) : MaterializationMetamodel
