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

    override val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement> =
        materializationMetamodelFacts.querySchemaElementsByPath

    override val fieldCoordinatesByPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>> =
        materializationMetamodelFacts.fieldCoordinatesByPath

    override val pathsByFieldCoordinates:
        ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>> =
        materializationMetamodelFacts.pathsByFieldCoordinates
}
