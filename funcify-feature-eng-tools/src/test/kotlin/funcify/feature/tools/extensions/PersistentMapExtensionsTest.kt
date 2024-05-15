package funcify.feature.tools.extensions

import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentListValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PersistentMapExtensionsTest {

    @Test
    fun parallelOrderPreservationTest() {
        val outputMap: PersistentMap<Int, Char> =
            (1..200)
                .toList()
                .stream()
                .parallel()
                .filter { i -> i % 3 != 0 }
                .flatMap { i ->
                    if (i and 1 == 0) {
                        Stream.of(i)
                    } else {
                        Stream.empty()
                    }
                }
                .map { i -> i to ('A'.code + i).toChar() }
                .reducePairsToPersistentMap()
        Assertions.assertEquals(('A'.code + 2).toChar(), outputMap.asIterable().first().value)
        Assertions.assertEquals(
            ('A'.code + 100).toChar(),
            outputMap
                .asIterable()
                .filterIndexed { index, entry -> index == (outputMap.size shr 1) }
                .first()
                .value
        )
        Assertions.assertEquals(('A'.code + 200).toChar(), outputMap.asIterable().last().value)
    }

    @Test
    fun sequentialOrderPreservationTest() {
        val outputMap: PersistentMap<Int, Char> =
            (1..200)
                .toList()
                .stream()
                .filter { i -> i % 3 != 0 }
                .flatMap { i ->
                    if (i and 1 == 0) {
                        Stream.of(i)
                    } else {
                        Stream.empty()
                    }
                }
                .map { i -> i to ('A'.code + i).toChar() }
                .reducePairsToPersistentMap()
        Assertions.assertEquals(('A'.code + 2).toChar(), outputMap.asIterable().first().value)
        Assertions.assertEquals(
            ('A'.code + 100).toChar(),
            outputMap
                .asIterable()
                .filterIndexed { index, _ -> index == (outputMap.size shr 1) }
                .first()
                .value
        )
        Assertions.assertEquals(('A'.code + 200).toChar(), outputMap.asIterable().last().value)
    }

    @Test
    fun parallelOrderPersistentSetValuePreservationTest() {
        val outputMap: PersistentMap<Int, PersistentSet<Char>> =
            (0..25)
                .toList()
                .stream()
                .map { i -> i to ('A'.code + i).toChar() }
                .reduce(
                    persistentMapOf<Int, PersistentSet<Char>>(),
                    { pm, pair ->
                        val key = pair.first % 26 / 5
                        pm.put(key, pm.getOrDefault(key, persistentSetOf()).add(pair.second))
                    },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val resultMap =
            outputMap
                .streamPairs()
                .map { pair -> pair.first.let { 'A' + it }.toChar() to pair.second }
                .flatMap { pair -> pair.second.stream().map { c -> pair.first to c } }
                .parallel()
                .reducePairsToPersistentSetValueMap()
        Assertions.assertEquals('A', resultMap.asIterable().first().key)
        Assertions.assertEquals('A', resultMap.asIterable().first().value.first())
        Assertions.assertEquals('E', resultMap.asIterable().first().value.last())
        Assertions.assertEquals('F', resultMap.asIterable().last().key)
        Assertions.assertEquals('Z', resultMap.asIterable().last().value.first())
    }

    @Test
    fun persistentListValueMapOrderCombinationTest() {
        val plvm1: PersistentMap<Int, PersistentList<String>> =
            (0..25)
                .toList()
                .stream()
                .map { i -> i to ('A'.code + i).toChar() }
                .reduce(
                    persistentMapOf<Int, PersistentList<String>>(),
                    { pm, pair ->
                        val key = pair.first % 26 / 5
                        pm.put(
                            key,
                            pm.getOrDefault(key, persistentListOf()).add(pair.second.toString())
                        )
                    },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val plvm2: PersistentMap<Int, PersistentList<String>> =
            (0..25)
                .toList()
                .stream()
                .map { i -> i to ('A'.code + i).toChar() }
                .reduce(
                    persistentMapOf<Int, PersistentList<String>>(),
                    { pm, pair ->
                        val key = pair.first % 26 / 5
                        pm.put(
                            key,
                            pm.getOrDefault(key, persistentListOf()).add(pair.first.toString())
                        )
                    },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val combinedMap: PersistentMap<Int, PersistentList<String>> =
            Assertions.assertDoesNotThrow<PersistentMap<Int, PersistentList<String>>> {
                plvm1.combineWithPersistentListValueMap(plvm2)
            }
        //println(combinedMap)
        Assertions.assertTrue(combinedMap.containsKey(0)) { "contains list at index 0" }
        Assertions.assertEquals(10, combinedMap[0]?.size) { "first group is of size 10" }
        Assertions.assertEquals("0", combinedMap[0]?.get(10 shr 1)) { "has 0 at index 4" }
        Assertions.assertEquals("A", combinedMap[0]?.get(0)) { "has A at index 0" }
        Assertions.assertTrue(combinedMap.containsKey(5)) { "has a list at index 5" }
        Assertions.assertEquals(2, combinedMap[5]?.size) { "last group is of size 2" }
        Assertions.assertEquals(persistentListOf("Z", "25"), combinedMap[5]) { "last group" }
    }
}
