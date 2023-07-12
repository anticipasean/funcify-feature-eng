package funcify.feature.schema.dataelement

import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-28
 */
interface DataElementSourceProvider<out DES : DataElementSource> {

    val name: String
    
    fun getLatestDataElementSource(): Mono<out DES>
}
