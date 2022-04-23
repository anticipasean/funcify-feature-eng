package funcify.feature.tools.extensions

import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PersistentListExtensionsTest {

    @Test
    fun parallelOrderPreservationTest() {
        val outputList: PersistentList<Int> =
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
                .reduceToPersistentList()
        Assertions.assertEquals(2, outputList.first())
        Assertions.assertEquals(100, outputList[outputList.size shr 1])
        Assertions.assertEquals(200, outputList.last())
    }

    @Test
    fun sequentialOrderPreservationTest() {
        val outputList: PersistentList<Int> =
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
                .reduceToPersistentList()
        Assertions.assertEquals(2, outputList.first())
        Assertions.assertEquals(100, outputList[outputList.size shr 1])
        Assertions.assertEquals(200, outputList.last())
    }
}
