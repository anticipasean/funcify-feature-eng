package funcify.feature.tools.extensions

import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * @author smccarron
 * @created 4/5/22
 */
object PersistentMapExtensions {

    fun <K, V> ImmutableMap<K, V>?.streamEntries(): Stream<Map.Entry<K, V>> {
        return this?.entries?.stream() ?: Stream.empty()
    }

    fun <K, V> ImmutableMap<K, V>?.streamPairs(): Stream<Pair<K, V>> {
        return this?.entries?.stream()?.map(Map.Entry<K, V>::toPair) ?: Stream.empty()
    }

    fun <K, V> Sequence<Map.Entry<K, V>>.toPersistentMap(): PersistentMap<K, V> {
        return this.reduceEntriesToPersistentMap()
    }

    fun <K, V> Sequence<Map.Entry<K, V>>.reduceEntriesToPersistentMap(): PersistentMap<K, V> {
        return this.fold(persistentMapOf()) { pm: PersistentMap<K, V>, (k: K, v: V) ->
            pm.put(k, v)
        }
    }

    fun <K, V> Sequence<Pair<K, V>>.reducePairsToPersistentMap(): PersistentMap<K, V> {
        return this.fold(persistentMapOf()) { pm, (k: K, v: V) -> pm.put(k, v) }
    }

    fun <K, V> Iterator<Map.Entry<K, V>>.reduceEntriesToPersistentMap(): PersistentMap<K, V> {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, 0), true)
            .reduceEntriesToPersistentMap()
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentMap(): PersistentMap<K, V> {
        return this.reduce(
            persistentMapOf<K, V>(),
            { pm: PersistentMap<K, V>, (k: K, v: V): Map.Entry<K, V> -> pm.put(k, v) },
            { pm1: PersistentMap<K, V>, pm2: PersistentMap<K, V> -> pm1.putAll(pm2) }
        )
    }

    fun <K, V> Stream<Map.Entry<K, V>>.toPersistentMap(): PersistentMap<K, V> {
        return this.reduceEntriesToPersistentMap()
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToImmutableMap(): ImmutableMap<K, V> {
        return this.reduceEntriesToPersistentMap()
    }

    fun <K, V> Stream<Map.Entry<K, V>>.toImmutableMap(): ImmutableMap<K, V> {
        return this.reduceEntriesToImmutableMap()
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentMap(): PersistentMap<K, V> {
        return this.reduce(
            persistentMapOf<K, V>(),
            { pm: PersistentMap<K, V>, (k: K, v: V): Pair<K, V> -> pm.put(k, v) },
            { pm1: PersistentMap<K, V>, pm2: PersistentMap<K, V> -> pm1.putAll(pm2) }
        )
    }

    fun <K, V> Iterator<Pair<K, V>>.reducePairsToPersistentMap(): PersistentMap<K, V> {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, 0), true)
            .reducePairsToPersistentMap()
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToImmutableMap(): ImmutableMap<K, V> {
        return this.reducePairsToPersistentMap()
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentSetValueMap(
        startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(
            startValue,
            { pm: PersistentMap<K, PersistentSet<V>>, (k: K, v: V): Pair<K, V> ->
                pm.put(k, pm.getOrDefault(k, persistentHashSetOf()).add(v))
            },
            { pm1: PersistentMap<K, PersistentSet<V>>, pm2: PersistentMap<K, PersistentSet<V>> ->
                pm1.combineWithPersistentSetValueMap(pm2)
            }
        )
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(
        startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.reduce(
            startValue,
            { pm: PersistentMap<K, PersistentSet<V>>, (k: K, v: V): Map.Entry<K, V> ->
                pm.put(k, pm.getOrDefault(k, persistentSetOf()).add(v))
            },
            { pm1: PersistentMap<K, PersistentSet<V>>, pm2: PersistentMap<K, PersistentSet<V>> ->
                pm2.combineWithPersistentSetValueMap(pm1)
            }
        )
    }

    fun <K, V> PersistentMap<K, PersistentSet<V>>.combineWithPersistentSetValueMap(
        otherMap: PersistentMap<K, PersistentSet<V>>
    ): PersistentMap<K, PersistentSet<V>> {
        val thisMapBuilder: PersistentMap.Builder<K, PersistentSet<V>> = this.builder()
        for ((k: K, v: PersistentSet<V>) in otherMap) {
            thisMapBuilder[k] = thisMapBuilder.getOrElse(k, ::persistentSetOf).addAll(v)
        }
        return thisMapBuilder.build()
    }

    fun <K, V> Sequence<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(
        startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.fold(startValue) {
            pm: PersistentMap<K, PersistentSet<V>>,
            (k: K, v: V): Map.Entry<K, V> ->
            pm.put(k, pm.getOrElse(k, ::persistentSetOf).add(v))
        }
    }

    fun <K, V> Sequence<Pair<K, V>>.reducePairsToPersistentSetValueMap(
        startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentSet<V>> {
        return this.fold(startValue) {
            pm: PersistentMap<K, PersistentSet<V>>,
            (k: K, v: V): Pair<K, V> ->
            pm.put(k, pm.getOrElse(k, ::persistentSetOf).add(v))
        }
    }

    fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentListValueMap(
        startValue: PersistentMap<K, PersistentList<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentList<V>> {
        return this.reduce(
            startValue,
            { pm: PersistentMap<K, PersistentList<V>>, (k: K, v: V): Pair<K, V> ->
                pm.put(k, pm.getOrElse(k, ::persistentListOf).add(v))
            },
            { pm1: PersistentMap<K, PersistentList<V>>, pm2: PersistentMap<K, PersistentList<V>> ->
                pm1.combineWithPersistentListValueMap(pm2)
            }
        )
    }

    fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentListValueMap(
        startValue: PersistentMap<K, PersistentList<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentList<V>> {
        return this.reduce(
            startValue,
            { pm: PersistentMap<K, PersistentList<V>>, (k: K, v: V): Map.Entry<K, V> ->
                pm.put(k, pm.getOrElse(k, ::persistentListOf).add(v))
            },
            { pm1: PersistentMap<K, PersistentList<V>>, pm2: PersistentMap<K, PersistentList<V>> ->
                pm1.combineWithPersistentListValueMap(pm2)
            }
        )
    }

    fun <K, V> PersistentMap<K, PersistentList<V>>.combineWithPersistentListValueMap(
        otherMap: PersistentMap<K, PersistentList<V>>
    ): PersistentMap<K, PersistentList<V>> {
        val thisMapBuilder: PersistentMap.Builder<K, PersistentList<V>> = this.builder()
        for ((k: K, v: PersistentList<V>) in otherMap) {
            thisMapBuilder[k] = thisMapBuilder.getOrElse(k, ::persistentListOf).addAll(v)
        }
        return thisMapBuilder.build()
    }

    fun <K, V> Sequence<Map.Entry<K, V>>.reduceEntriesToPersistentListValueMap(
        startValue: PersistentMap<K, PersistentList<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentList<V>> {
        return this.fold(startValue) {
            pm: PersistentMap<K, PersistentList<V>>,
            (k: K, v: V): Map.Entry<K, V> ->
            pm.put(k, pm.getOrElse(k, ::persistentListOf).add(v))
        }
    }

    fun <K, V> Sequence<Pair<K, V>>.reducePairsToPersistentListValueMap(
        startValue: PersistentMap<K, PersistentList<V>> = persistentMapOf()
    ): PersistentMap<K, PersistentList<V>> {
        return this.fold(startValue) {
            pm: PersistentMap<K, PersistentList<V>>,
            (k: K, v: V): Pair<K, V> ->
            pm.put(k, pm.getOrElse(k, ::persistentListOf).add(v))
        }
    }
}
