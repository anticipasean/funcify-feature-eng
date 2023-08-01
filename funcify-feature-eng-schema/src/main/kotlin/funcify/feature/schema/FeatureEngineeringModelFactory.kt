package funcify.feature.schema

/**
 * @author smccarron
 * @created 2023-07-09
 */
interface FeatureEngineeringModelFactory {

    fun builder(): FeatureEngineeringModel.Builder
}
