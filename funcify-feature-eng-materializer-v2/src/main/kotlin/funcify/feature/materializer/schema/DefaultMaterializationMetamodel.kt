package funcify.feature.materializer.schema

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
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
    private val materializationMetamodelFacts: MaterializationMetamodelFacts,
) : MaterializationMetamodel {

    override val childCanonicalPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>> =
        materializationMetamodelFacts.childCanonicalPathsByParentPath

    override val querySchemaElementsByCanonicalPath:
        ImmutableMap<GQLOperationPath, GraphQLSchemaElement> =
        materializationMetamodelFacts.querySchemaElementsByCanonicalPath

    override val fieldCoordinatesByCanonicalPath: ImmutableMap<GQLOperationPath, FieldCoordinates> =
        materializationMetamodelFacts.fieldCoordinatesByCanonicalPath

    override val canonicalPathsByFieldCoordinates:
        ImmutableMap<FieldCoordinates, GQLOperationPath> =
        materializationMetamodelFacts.canonicalPathsByFieldCoordinates

    override val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource> =
        materializationMetamodelFacts.domainSpecifiedDataElementSourceByPath

    override val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator> =
        materializationMetamodelFacts.featureSpecifiedFeatureCalculatorsByPath

    override val featurePathsByName: ImmutableMap<String, GQLOperationPath> =
        materializationMetamodelFacts.featurePathsByName
}
