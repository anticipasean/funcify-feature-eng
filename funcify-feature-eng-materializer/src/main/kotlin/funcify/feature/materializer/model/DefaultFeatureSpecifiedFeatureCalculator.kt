package funcify.feature.materializer.model

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.lastOrNone
import funcify.feature.error.ServiceError
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-19
 */
internal data class DefaultFeatureSpecifiedFeatureCalculator(
    override val featureFieldCoordinates: FieldCoordinates,
    override val featureName: String,
    override val featurePath: GQLOperationPath,
    override val featureFieldDefinition: GraphQLFieldDefinition,
    override val featureCalculator: FeatureCalculator,
    override val transformerFieldCoordinates: FieldCoordinates
) : FeatureSpecifiedFeatureCalculator {

    override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
        featureFieldDefinition.arguments
            .asSequence()
            .map { a: GraphQLArgument -> a.name to a }
            .reducePairsToPersistentMap()
    }

    override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        argumentsByName
            .asSequence()
            .map { (n: String, a: GraphQLArgument) -> featurePath.transform { argument(n) } to a }
            .reducePairsToPersistentMap()
    }

    companion object {

        fun builder(): FeatureSpecifiedFeatureCalculator.Builder {
            return DefaultBuilder()
        }

        internal class DefaultBuilder(
            private var featureFieldCoordinates: FieldCoordinates? = null,
            private var featureName: String? = null,
            private var featurePath: GQLOperationPath? = null,
            private var featureFieldDefinition: GraphQLFieldDefinition? = null,
            private var featureCalculator: FeatureCalculator? = null,
            private var transformerFieldCoordinates: FieldCoordinates? = null,
        ) : FeatureSpecifiedFeatureCalculator.Builder {

            override fun featureFieldCoordinates(
                featureFieldCoordinates: FieldCoordinates
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureFieldCoordinates = featureFieldCoordinates }

            override fun featureName(
                featureName: String
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureName = featureName }

            override fun featurePath(
                featurePath: GQLOperationPath
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featurePath = featurePath }

            override fun featureFieldDefinition(
                featureFieldDefinition: GraphQLFieldDefinition
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureFieldDefinition = featureFieldDefinition }

            override fun featureCalculator(
                featureCalculator: FeatureCalculator
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.featureCalculator = featureCalculator }

            override fun transformerFieldCoordinates(
                transformerFieldCoordinates: FieldCoordinates
            ): FeatureSpecifiedFeatureCalculator.Builder =
                this.apply { this.transformerFieldCoordinates = transformerFieldCoordinates }

            override fun build(): FeatureSpecifiedFeatureCalculator {
                return eagerEffect<String, FeatureSpecifiedFeatureCalculator> {
                        ensureNotNull(featureFieldCoordinates) {
                            "feature_field_coordinates not provided"
                        }
                        ensureNotNull(featurePath) { "feature_path not provided" }
                        ensureNotNull(featureFieldDefinition) {
                            "feature_field_definition not provided"
                        }
                        ensureNotNull(featureCalculator) { "feature_calculator not provided" }
                        ensure(
                            featureFieldCoordinates!!.fieldName == featureFieldDefinition!!.name
                        ) {
                            "feature_field_coordinates.field_name does not match feature_field_definition.name"
                        }
                        ensure(!featurePath!!.containsAliasForField()) {
                            "feature_path cannot be aliased"
                        }
                        ensure(
                            featurePath!!
                                .selection
                                .lastOrNone()
                                .filterIsInstance<FieldSegment>()
                                .map { fs: FieldSegment ->
                                    fs.fieldName == featureFieldCoordinates!!.fieldName
                                }
                                .getOrElse { false }
                        ) {
                            "feature_path[-1].field_name does not match feature_field_coordinates.field_name"
                        }
                        ensureNotNull(transformerFieldCoordinates) {
                            "transformer_field_coordinates not provided"
                        }
                        DefaultFeatureSpecifiedFeatureCalculator(
                            featureFieldCoordinates = featureFieldCoordinates!!,
                            featureName = featureName ?: featureFieldDefinition!!.name,
                            featurePath = featurePath!!,
                            featureFieldDefinition = featureFieldDefinition!!,
                            featureCalculator = featureCalculator!!,
                            transformerFieldCoordinates = transformerFieldCoordinates!!
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create instance of [ type: %s ] due to [ message: %s ]",
                                FeatureSpecifiedFeatureCalculator::class.qualifiedName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }
    }
}
