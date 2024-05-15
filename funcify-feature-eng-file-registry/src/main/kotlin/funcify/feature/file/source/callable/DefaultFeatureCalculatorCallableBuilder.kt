package funcify.feature.file.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import funcify.feature.error.ServiceError
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.transformer.TransformerCallable

internal class DefaultFeatureCalculatorCallableBuilder(
    private var featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator? = null,
    private var transformerCallable: TransformerCallable? = null
) : FeatureCalculatorCallable.Builder {

    override fun selectFeature(
        featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator
    ): FeatureCalculatorCallable.Builder =
        this.apply { this.featureSpecifiedFeatureCalculator = featureSpecifiedFeatureCalculator }

    override fun setTransformerCallable(
        transformerCallable: TransformerCallable
    ): FeatureCalculatorCallable.Builder =
        this.apply { this.transformerCallable = transformerCallable }

    override fun build(): FeatureCalculatorCallable {
        return eagerEffect<String, FeatureCalculatorCallable> {
                ensureNotNull(featureSpecifiedFeatureCalculator) {
                    "feature_specified_feature_calculator not provided"
                }
                ensureNotNull(transformerCallable) { "transformer_callable not provided" }
                DefaultFeatureCalculatorCallable(
                    featureSpecifiedFeatureCalculator = featureSpecifiedFeatureCalculator!!,
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
