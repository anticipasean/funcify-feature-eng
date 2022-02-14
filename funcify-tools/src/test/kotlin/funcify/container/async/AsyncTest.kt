package funcify.container.async

import arrow.core.getOrElse
import arrow.core.some
import kotlinx.collections.immutable.toPersistentList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.util.concurrent.CompletableFuture
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.streams.asSequence


/**
 *
 * @author smccarron
 * @created 2/10/22
 */
class AsyncTest {

    @Test
    fun syncPartitionTest() {
        val intStream = IntStream.range(0, 4)
                .boxed()
        val streamPairsAsync = Async.succeeded(intStream.asSequence()
                                                       .toPersistentList())
                .partition { i -> i % 2 == 0 }
        val pairAttempt = streamPairsAsync.block()
        Assertions.assertTrue(pairAttempt.isSuccess())
        val stream = pairAttempt.orElseGet { Stream.empty() }
        val (evens, odds) = stream.findFirst()
                .get()
                .some()
                .getOrElse { Pair(emptySequence(), emptySequence()) }
        val evensList: List<Int> = evens.toList()
        val oddsList: List<Int> = odds.toList()
        Assertions.assertEquals(2, evensList.size)
        Assertions.assertEquals(2, oddsList.size)
        Assertions.assertEquals(0, evensList[0])
        Assertions.assertEquals(1, oddsList[0])
    }

    @Test
    fun asyncFluxPartitionTest() {
        val intStream = IntStream.range(0, 4)
                .boxed()
        val streamPairsAsync = Async.fromFlux(Flux.fromStream(intStream))
                .partition { i -> i % 2 == 0 }
        val pairAttempt = streamPairsAsync.block()
        Assertions.assertTrue(pairAttempt.isSuccess())
        val stream = pairAttempt.orElseGet { Stream.empty() }
        val (evens, odds) = stream.findFirst()
                .get()
                .some()
                .getOrElse { Pair(emptySequence(), emptySequence()) }
        val evensList: List<Int> = evens.toList()
        val oddsList: List<Int> = odds.toList()
        Assertions.assertEquals(2, evensList.size)
        Assertions.assertEquals(2, oddsList.size)
        Assertions.assertEquals(0, evensList[0])
        Assertions.assertEquals(1, oddsList[0])
    }

    @Test
    fun asyncCompletionStagePartitionTest() {
        val intStream = IntStream.range(0, 4)
                .boxed()
        val streamPairsAsync = Async.fromCompletionStage(CompletableFuture.supplyAsync({ intStream }))
                .partition { i -> i % 2 == 0 }
        val pairAttempt = streamPairsAsync.block()
        Assertions.assertTrue(pairAttempt.isSuccess())
        val stream = pairAttempt.orElseGet { Stream.empty() }
        val (evens, odds) = stream.findFirst()
                .get()
                .some()
                .getOrElse { Pair(emptySequence(), emptySequence()) }
        val evensList: List<Int> = evens.toList()
        val oddsList: List<Int> = odds.toList()
        Assertions.assertEquals(2, evensList.size)
        Assertions.assertEquals(2, oddsList.size)
        Assertions.assertEquals(0, evensList[0])
        Assertions.assertEquals(1, oddsList[0])
    }

}