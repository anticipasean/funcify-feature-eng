package funcify.feature.tools.extensions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.Spliterators
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object PersistentMapExtensions {

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
                           { pm1, pm2 -> pm2.putAll(pm1) })
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentMap(): PersistentMap<K, V> {
        return this.reduce(persistentMapOf<K, V>(),
                           { pm, pair ->
                               pm.put(pair.first,
                                      pair.second)
                           },
                           { pm1, pm2 -> pm2.putAll(pm1) })
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf(),
                                                                     filter: (K) -> Boolean = { true }): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(startValue,
                           { pm, pair ->
                               if (filter.invoke(pair.first)) {
                                   pm.put(pair.first,
                                          pm.getOrDefault(pair.first,
                                                          persistentHashSetOf())
                                                  .add(pair.second))
                               } else {
                                   pm
                               }
                           },
                           { pm1, pm2 ->
                               pm2.combineWithPersistentSetValueMap(pm1)
                           })
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf(),
                                                                            filter: (K) -> Boolean = { true }): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(startValue,
                           { pm, entry ->
                               if (filter.invoke(entry.key)) {
                                   pm.put(entry.key,
                                          pm.getOrDefault(entry.key,
                                                          persistentSetOf())
                                                  .add(entry.value))
                               } else {
                                   pm
                               }
                           },
                           { pm1, pm2 ->
                               pm2.combineWithPersistentSetValueMap(pm1)
                           })
    }

    fun <K, V> PersistentMap<K, PersistentSet<V>>.combineWithPersistentSetValueMap(otherMap: PersistentMap<K, PersistentSet<V>>): PersistentMap<K, PersistentSet<V>> {
        return when {
            this.isEmpty() -> {
                otherMap
            }
            otherMap.isEmpty() -> {
                this
            }
            this.size > otherMap.size -> {
                val finalResultHolder: Array<PersistentMap<K, PersistentSet<V>>> = arrayOf(this)
                otherMap.forEach({ (key, value) ->
                                     finalResultHolder[0] = finalResultHolder[0].put(key,
                                                                                     finalResultHolder[0].getOrDefault(key,
                                                                                                                       persistentSetOf())
                                                                                             .addAll(value))
                                 })
                finalResultHolder[0]
            }
            else -> {
                val finalResultHolder: Array<PersistentMap<K, PersistentSet<V>>> = arrayOf(otherMap)
                this.forEach({ (key, value) ->
                                 finalResultHolder[0] = finalResultHolder[0].put(key,
                                                                                 finalResultHolder[0].getOrDefault(key,
                                                                                                                   persistentSetOf())
                                                                                         .addAll(value))
                             })
                finalResultHolder[0]
            }
        }
    }


}