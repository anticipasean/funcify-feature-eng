package funcify.feature.materializer.loader

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-28
 */
fun interface ReactiveBatchDataLoader<K, V> {

    fun load(
        inputArguments: ImmutableMap<K, V>,
        outputKeys: ImmutableSet<K>
    ): Mono<out ImmutableMap<K, V>>
}
