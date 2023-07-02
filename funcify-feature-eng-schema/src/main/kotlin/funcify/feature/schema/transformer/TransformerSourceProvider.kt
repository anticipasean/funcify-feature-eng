package funcify.feature.schema.transformer

import funcify.feature.schema.SourceType
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSourceProvider<TS : TransformerSource> {

    val name: String

    val sourceType: SourceType

    fun getLatestTransformerSource(): Mono<TS>
}
