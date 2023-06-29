package funcify.feature.schema.dataelementsource

import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-28
 */
interface DataElementSourceProvider<out DES : DataElementSource> {

    val name: String

    val sourceType: SourceType

    fun getLatestDataElementSource(): Mono<out DES>
}
