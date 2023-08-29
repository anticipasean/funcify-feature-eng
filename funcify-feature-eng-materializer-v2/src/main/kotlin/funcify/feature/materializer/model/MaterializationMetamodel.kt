package funcify.feature.materializer.model

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistry
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-04
 */
interface MaterializationMetamodel {

    val created: Instant

    val featureEngineeringModel: FeatureEngineeringModel

    val materializationGraphQLSchema: GraphQLSchema

    val aliasCoordinatesRegistry: AliasCoordinatesRegistry

    val childPathsByParentPath: ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

    val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

    val fieldCoordinatesByPath: ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>

    val pathsByFieldCoordinates: ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>

    val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>

    val domainSpecifiedDataElementSourceByCoordinates:
        ImmutableMap<FieldCoordinates, DomainSpecifiedDataElementSource>

    val dataElementFieldCoordinatesByFieldName: ImmutableMap<String, ImmutableSet<FieldCoordinates>>

    val dataElementPathsByFieldName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>

    val dataElementPathByFieldArgumentName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>

    val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>

    val featurePathsByName: ImmutableMap<String, GQLOperationPath>

    interface Builder {

        fun featureEngineeringModel(featureEngineeringModel: FeatureEngineeringModel): Builder

        fun materializationGraphQLSchema(materializationGraphQLSchema: GraphQLSchema): Builder

        fun build(): Mono<out MaterializationMetamodel>
    }
}
