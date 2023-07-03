package funcify.feature.schema.feature

import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface FeatureCalculatorProvider<out FC : FeatureCalculator> {

    val name: String

    fun getLatestFeatureCalculator(): Mono<out FC>
}
