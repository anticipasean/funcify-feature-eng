package funcify.feature.materializer.loader

import arrow.core.Option
import kotlinx.collections.immutable.ImmutableList
import reactor.core.Disposable

/**
 * @author smccarron
 * @created 2023-07-28
 */
interface ReactiveDataLoaderRegistry<K> {

    fun register(
        key: K,
        reactiveDataLoader: ReactiveDataLoader<*, *>
    ): ReactiveDataLoaderRegistry<K>

    fun unregister(key: K): ReactiveDataLoaderRegistry<K>

    fun getOrNone(key: K): Option<ReactiveDataLoader<*, *>>

    fun getKeys(): ImmutableList<K>

    fun getReactiveDataLoaders(): ImmutableList<ReactiveDataLoader<*, *>>

    fun dispatchAll(): Disposable
}
