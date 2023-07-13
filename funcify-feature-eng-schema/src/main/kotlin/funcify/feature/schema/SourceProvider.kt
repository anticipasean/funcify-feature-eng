package funcify.feature.schema

import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-07-13
 */
interface SourceProvider<out S : Source> {

    val name: String

    fun getLatestSource(): Mono<out S>

}
