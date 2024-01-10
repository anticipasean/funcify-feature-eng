package funcify.feature.materializer.loader

import arrow.core.Option
import arrow.core.getOrNone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import reactor.core.Disposable
import reactor.core.publisher.Flux

/**
 * @author smccarron
 * @created 2023-07-28
 */
internal data class DefaultReactiveDataLoaderRegistry<K>(
    private val reactiveDataLoadersByKey: PersistentMap<K, ReactiveDataLoader<*, *>> =
        persistentMapOf()
) : ReactiveDataLoaderRegistry<K> {

    override fun register(
        key: K,
        reactiveDataLoader: ReactiveDataLoader<*, *>
    ): ReactiveDataLoaderRegistry<K> {
        return this.copy(
            reactiveDataLoadersByKey = reactiveDataLoadersByKey.put(key, reactiveDataLoader)
        )
    }

    override fun unregister(key: K): ReactiveDataLoaderRegistry<K> {
        return if (key in reactiveDataLoadersByKey) {
            this.copy(reactiveDataLoadersByKey = reactiveDataLoadersByKey.remove(key))
        } else {
            this
        }
    }

    override fun getOrNone(key: K): Option<ReactiveDataLoader<*, *>> {
        return reactiveDataLoadersByKey.getOrNone(key)
    }

    override fun getKeys(): ImmutableList<K> {
        return reactiveDataLoadersByKey.keys.toPersistentList()
    }

    override fun getReactiveDataLoaders(): ImmutableList<ReactiveDataLoader<*, *>> {
        return reactiveDataLoadersByKey.values.toPersistentList()
    }

    override fun dispatchAll(): Disposable {
        return Flux.fromIterable(reactiveDataLoadersByKey.values).subscribe {
            d: ReactiveDataLoader<*, *> ->
            d.dispatch()
        }
    }
}
