package funcify.feature.tools.extensions

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object PersistentMapExtensions {

    fun <K, V> ImmutableMap<K, V>?.streamEntries() : Stream<Map.Entry<K, V>> {
        return this?.entries?.stream() ?: Stream.empty()
    }

    fun <K, V> ImmutableMap<K, V>?.streamPairs() : Stream<Pair<K, V>> {
        return this?.entries?.stream()?.map { entry: Map.Entry<K, V> -> entry.key to entry.value } ?: Stream.empty()
    }

    fun <K, V> Iterator<Map.Entry<K, V>>.reduceEntriesToPersistentMap(): PersistentMap<K, V> {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this,
                                                                        0),
                                    true)
                .reduceEntriesToPersistentMap()
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentMap(): PersistentMap<K, V> {
        return this.reduce(persistentMapOf<K, V>(),
                           { pm, entry ->
                               pm.put(entry.key,
                                      entry.value)
                           },
                           { pm1, pm2 ->
                               /**
                                * pm1 putAll pm2 preserves insertion order
                                * if backing implementation is of ordered type
                                * and stream is parallel
                                */
                               pm1.putAll(pm2)
                           })
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentMap(): PersistentMap<K, V> {
        return this.reduce(persistentMapOf<K, V>(),
                           { pm, pair ->
                               pm.put(pair.first,
                                      pair.second)
                           },
                           { pm1, pm2 ->
                               /**
                                * pm1 putAll pm2 preserves insertion order
                                * if backing implementation is of ordered type
                                * and stream is parallel
                                */
                               pm1.putAll(pm2)
                           })
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(startValue,
                           { pm, pair ->
                               pm.put(pair.first,
                                      pm.getOrDefault(pair.first,
                                                      persistentHashSetOf())
                                              .add(pair.second))
                           },
                           { pm1, pm2 -> pm2.combineWithPersistentSetValueMap(pm1) })
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(startValue,
                           { pm, entry ->
                               pm.put(entry.key,
                                      pm.getOrDefault(entry.key,
                                                      persistentSetOf())
                                              .add(entry.value))
                           },
                           { pm1, pm2 ->
                               pm2.combineWithPersistentSetValueMap(pm1)
                           })
    }

    fun <K, V> PersistentMap<K, PersistentSet<V>>.combineWithPersistentSetValueMap(otherMap: PersistentMap<K, PersistentSet<V>>): PersistentMap<K, PersistentSet<V>> {
        /**
         * invoking #put on this for each entry from other preserves insertion order
         */
        val finalResultHolder: Array<PersistentMap<K, PersistentSet<V>>> = arrayOf(otherMap)
        /**
         * performing this.putAll(other) would potentially overwrite entries in this for a given key K
         * { ["Bob"]: { 1, 2, 3 } }.putAll({ ["Bob"]: { 3, 4, 5 } }) => { ["Bob"]: { 3, 4, 5 } }
         * so combining the set values this\[K] + other\[K] in the following manner avoids a loss of information
         * { ["Bob"]: { 1, 2, 3 } }.forEach((k, v) -> (other["Bob"] || {}).addAll(v) ) => { ["Bob"]: { 1, 2, 3, 4, 5 } }
         */
        this.forEach({ (key, value) ->
                         finalResultHolder[0] = finalResultHolder[0].put(key,
                                                                         finalResultHolder[0].getOrDefault(key,
                                                                                                           persistentSetOf())
                                                                                 .addAll(value))
                     })
        return finalResultHolder[0]
    }

}
