package funcify.feature.file.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition

internal class DefaultFeatureCalculatorCallableBuilder(
    private var featureFieldCoordinates: FieldCoordinates? = null,
    private var featurePath: GQLOperationPath? = null,
    private var featureGraphQLFieldDefinition: GraphQLFieldDefinition? = null,
    private var transformerCallable: TransformerCallable? = null
) : FeatureCalculatorCallable.Builder {

    override fun selectFeature(
        coordinates: FieldCoordinates,
        path: GQLOperationPath,
        graphQLFieldDefinition: GraphQLFieldDefinition,
    ): FeatureCalculatorCallable.Builder =
        this.apply {
            featureFieldCoordinates = coordinates
            featurePath = path
            featureGraphQLFieldDefinition = graphQLFieldDefinition
        }

    override fun setTransformerCallable(
        transformerCallable: TransformerCallable
    ): FeatureCalculatorCallable.Builder =
        this.apply { this.transformerCallable = transformerCallable }

    override fun build(): FeatureCalculatorCallable {
        return eagerEffect<String, FeatureCalculatorCallable> {
                ensureNotNull(featureFieldCoordinates) { "feature_field_coordinates not provided" }
                ensureNotNull(featurePath) { "feature_path not provided" }
                ensureNotNull(featureGraphQLFieldDefinition) {
                    "feature_graphql_field_definition not provided"
                }
                ensureNotNull(transformerCallable) { "transformer_callable not provided" }
                DefaultFeatureCalculatorCallable(
                    featureCoordinates = featureFieldCoordinates!!,
                    featurePath = featurePath!!,
                    featureGraphQLFieldDefinition = featureGraphQLFieldDefinition!!,
                    transformerCallable = transformerCallable!!
                )
            }
            .fold(
                { message: String ->
                    throw ServiceError.of(
                        "unable to create %s [ message: %s ]",
                        DefaultFeatureCalculatorCallable::class.simpleName,
                        message
                    )
                },
                ::identity
            )
    }
}
