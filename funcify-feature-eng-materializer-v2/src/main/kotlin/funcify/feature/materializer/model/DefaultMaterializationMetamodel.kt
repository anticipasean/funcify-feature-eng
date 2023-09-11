package funcify.feature.materializer.model

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistry
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val featureEngineeringModel: FeatureEngineeringModel,
    override val materializationGraphQLSchema: GraphQLSchema,
    override val elementTypeCoordinates: ImmutableSet<FieldCoordinates>,
    override val elementTypePaths: ImmutableSet<GQLOperationPath>,
    override val dataElementElementTypePath: GQLOperationPath,
    override val featureElementTypePath: GQLOperationPath,
    override val transformerElementTypePath: GQLOperationPath,
    override val aliasCoordinatesRegistry: AliasCoordinatesRegistry,
    override val childPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>,
    override val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>,
    override val pathsByFieldCoordinates:
        ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>,
    override val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>,
    override val domainSpecifiedDataElementSourceByCoordinates:
        ImmutableMap<FieldCoordinates, DomainSpecifiedDataElementSource>,
    override val dataElementFieldCoordinatesByFieldName:
        ImmutableMap<String, ImmutableSet<FieldCoordinates>>,
    override val dataElementPathsByFieldName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>,
    override val dataElementPathByFieldArgumentName:
        ImmutableMap<String, ImmutableSet<GQLOperationPath>>,
    override val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>,
    override val featureSpecifiedFeatureCalculatorsByCoordinates:
        ImmutableMap<FieldCoordinates, FeatureSpecifiedFeatureCalculator>,
    override val featurePathsByName: ImmutableMap<String, GQLOperationPath>,
    override val featureCoordinatesByName: ImmutableMap<String, FieldCoordinates>,
    override val transformerSpecifiedTransformerSourcesByPath:
        ImmutableMap<GQLOperationPath, TransformerSpecifiedTransformerSource>,
    override val transformerSpecifiedTransformerSourcesByCoordinates:
        ImmutableMap<FieldCoordinates, TransformerSpecifiedTransformerSource>,
) : MaterializationMetamodel
