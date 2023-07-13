package funcify.feature.schema.dataelement

import funcify.feature.schema.SourceProvider
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-28
 */
interface DataElementSourceProvider<out DES : DataElementSource> : SourceProvider<DES> {

    override val name: String
    
    override fun getLatestSource(): Mono<out DES>
}
