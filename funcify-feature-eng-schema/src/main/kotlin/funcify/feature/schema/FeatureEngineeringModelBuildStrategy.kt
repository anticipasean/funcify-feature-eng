package funcify.feature.schema

import funcify.feature.schema.context.FeatureEngineeringModelBuildContext
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-12
 */
interface FeatureEngineeringModelBuildStrategy {

    fun buildFeatureEngineeringModel(
        context: FeatureEngineeringModelBuildContext
    ): Mono<out FeatureEngineeringModel>
}
