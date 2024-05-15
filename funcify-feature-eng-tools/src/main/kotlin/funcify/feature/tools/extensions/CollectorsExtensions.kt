package funcify.feature.tools.extensions

import arrow.core.identity
import java.util.stream.Collector
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus

/**
 * @author smccarron
 * @created 2024-02-17
 */
object CollectorsExtensions {

    fun <T> toPersistentList(): Collector<T, PersistentList<T>, PersistentList<T>> {
        return Collector.of(
            ::persistentListOf,
            PersistentList<T>::add,
            PersistentList<T>::addAll,
            java.util.function.Function.identity(),
            Collector.Characteristics.IDENTITY_FINISH
        )
    }

    fun <T> toPersistentSet(): Collector<T, PersistentSet<T>, PersistentSet<T>> {
        return Collector.of(
            ::persistentSetOf,
            PersistentSet<T>::add,
            PersistentSet<T>::addAll,
            java.util.function.Function.identity(),
            Collector.Characteristics.IDENTITY_FINISH
        )
    }

    fun <T, K, V> toPersistentMap(
        keyExtractor: (T) -> K,
        valueExtractor: (T) -> V
    ): Collector<T, PersistentMap<K, V>, PersistentMap<K, V>> {
        return Collector.of(
            ::persistentMapOf,
            { pm: PersistentMap<K, V>, t: T ->
                pm.apply { put(keyExtractor(t), valueExtractor(t)) }
            },
            PersistentMap<K, V>::plus,
            java.util.function.Function.identity(),
            Collector.Characteristics.IDENTITY_FINISH
        )
    }

    fun <T, K> toPersistentMap(
        keyExtractor: (T) -> K
    ): Collector<T, PersistentMap<K, T>, PersistentMap<K, T>> {
        return toPersistentMap(keyExtractor, ::identity)
    }
}
