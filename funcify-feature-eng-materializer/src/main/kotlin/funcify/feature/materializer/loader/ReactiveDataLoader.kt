package funcify.feature.materializer.loader

import kotlinx.collections.immutable.ImmutableMap
import reactor.core.Disposable
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-28
 */
interface ReactiveDataLoader<K, V> {

    companion object {

        fun <K, V> newLoader(
            reactiveBatchDataLoader: ReactiveBatchDataLoader<K, V>
        ): ReactiveDataLoader<K, V> {
            return DefaultReactiveDataLoader<K, V>(
                reactiveBatchDataLoader = reactiveBatchDataLoader
            )
        }
    }

    fun addArgument(key: K, value: V): ReactiveDataLoader<K, V>

    fun addArguments(arguments: Map<K, V>): ReactiveDataLoader<K, V>

    fun addArgumentPublisher(key: K, value: Mono<out V>): ReactiveDataLoader<K, V>

    fun addArgumentPublishers(arguments: Map<K, Mono<out V>>): ReactiveDataLoader<K, V>

    fun removeArgument(key: K): ReactiveDataLoader<K, V>

    fun loadDataForKey(key: K): Pair<ReactiveDataLoader<K, V>, Mono<out V>>

    fun loadDataForKeys(keys: Set<K>): Pair<ReactiveDataLoader<K, V>, Mono<out ImmutableMap<K, V>>>

    fun putDataForKey(key: K, value: V): ReactiveDataLoader<K, V>

    fun dispatch(): Disposable
}
