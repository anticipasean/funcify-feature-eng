package funcify.feature.graph.extensions

import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal object PersistentMapExtensions {

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentMap(
        initialMap: PersistentMap<K, V> = persistentMapOf<K, V>()
    ): PersistentMap<K, V> {
        return this.reduce(initialMap, { pm, (k, v) -> pm.put(k, v) }, PersistentMap<K, V>::putAll)
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentMap(
        initialMap: PersistentMap<K, V> = persistentMapOf<K, V>()
    ): PersistentMap<K, V> {
        return this.reduce(initialMap, { pm, (k, v) -> pm.put(k, v) }, PersistentMap<K, V>::putAll)
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(
        initialMap: PersistentMap<K, PersistentSet<V>> = persistentMapOf<K, PersistentSet<V>>()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(
            initialMap,
            { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.add(v)) },
            { pm1, pm2 ->
                val pm1Builder = pm1.builder()
                pm2.forEach { (k, vSet) ->
                    pm1Builder[k] = pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(vSet)
                }
                pm1Builder.build()
            }
        )
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentSetValueMap(
        initialMap: PersistentMap<K, PersistentSet<V>> = persistentMapOf<K, PersistentSet<V>>()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(
            initialMap,
            { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.add(v)) },
            { pm1, pm2 ->
                val pm1Builder = pm1.builder()
                pm2.forEach { (k, vSet) ->
                    pm1Builder[k] = pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(vSet)
                }
                pm1Builder.build()
            }
        )
    }
}
