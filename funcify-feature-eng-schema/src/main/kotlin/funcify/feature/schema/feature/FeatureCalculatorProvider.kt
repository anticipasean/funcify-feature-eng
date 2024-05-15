package funcify.feature.schema.feature

import funcify.feature.schema.SourceProvider
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface FeatureCalculatorProvider<out FC : FeatureCalculator> : SourceProvider<FC> {

    override val name: String

    override fun getLatestSource(): Mono<out FC>
}
