package funcify.feature.tools.extensions

import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
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
}
