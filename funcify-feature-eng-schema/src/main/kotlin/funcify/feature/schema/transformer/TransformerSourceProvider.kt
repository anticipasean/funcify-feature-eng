package funcify.feature.schema.transformer

import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSourceProvider<out TS : TransformerSource> {

    val name: String

    fun getLatestTransformerSource(): Mono<out TS>
}
