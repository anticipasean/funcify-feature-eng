package funcify.feature.schema.transformer

import funcify.feature.schema.SourceProvider
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSourceProvider<out TS : TransformerSource> : SourceProvider<TS> {

    override val name: String

    override fun getLatestSource(): Mono<out TS>
}
