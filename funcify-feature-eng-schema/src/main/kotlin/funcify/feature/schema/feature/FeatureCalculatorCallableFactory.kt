package funcify.feature.schema.feature

/**
 * @author smccarron
 * @created 2023-08-24
 */
interface FeatureCalculatorCallableFactory {

    fun builder(): FeatureCalculatorCallable.Builder
}
