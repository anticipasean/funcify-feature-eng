package funcify.feature.tools.container.async

/**
 *
 * @author smccarron
 * @created 2/10/22
 */
class AsyncTest {

    //    @Test
    //    fun syncPartitionTest() {
    //        val intStream = IntStream.range(0,
    //                                        4)
    //                .boxed()
    //        val streamPairsAsync = Async.succeeded(intStream.asSequence()
    //                                                       .toPersistentList())
    //                .partition { i -> i % 2 == 0 }
    //        val pairAttempt = streamPairsAsync.block()
    //        Assertions.assertTrue(pairAttempt.isSuccess())
    //        val stream = pairAttempt.orElseGet { persistentListOf() }
    //        val (evens, odds) = stream.stream()
    //                .findFirst()
    //                .orElseGet {
    //                    Pair(emptySequence(),
    //                         emptySequence())
    //                }
    //        val evensList: List<Int> = evens.toList()
    //        val oddsList: List<Int> = odds.toList()
    //        Assertions.assertEquals(2,
    //                                evensList.size)
    //        Assertions.assertEquals(2,
    //                                oddsList.size)
    //        Assertions.assertEquals(0,
    //                                evensList[0])
    //        Assertions.assertEquals(1,
    //                                oddsList[0])
    //    }
    //
    //    @Test
    //    fun asyncFluxPartitionTest() {
    //        val intStream = IntStream.range(0,
    //                                        4)
    //                .boxed()
    //        val streamPairsAsync = Async.fromFlux(Flux.fromStream(intStream))
    //                .partition { i -> i % 2 == 0 }
    //        val pairAttempt = streamPairsAsync.block()
    //        Assertions.assertTrue(pairAttempt.isSuccess())
    //        val stream = pairAttempt.orElseGet { persistentListOf() }
    //        val (evens, odds) = stream.stream()
    //                .findFirst()
    //                .orElseGet {
    //                    Pair(emptySequence(),
    //                         emptySequence())
    //                }
    //        val evensList: List<Int> = evens.toList()
    //        val oddsList: List<Int> = odds.toList()
    //        Assertions.assertEquals(2,
    //                                evensList.size)
    //        Assertions.assertEquals(2,
    //                                oddsList.size)
    //        Assertions.assertEquals(0,
    //                                evensList[0])
    //        Assertions.assertEquals(1,
    //                                oddsList[0])
    //    }
    //
    //    @Test
    //    fun asyncCompletionStagePartitionTest() {
    //        val intPersistentList = (0..4).toPersistentList()
    //        val streamPairsAsync = Async.fromCompletionStage(CompletableFuture.supplyAsync({
    //
    // intPersistentList
    //                                                                                       }))
    //                .partition { i -> i % 2 == 0 }
    //        val pairAttempt = streamPairsAsync.block()
    //        Assertions.assertTrue(pairAttempt.isSuccess())
    //        val stream = pairAttempt.orElseGet { persistentListOf() }
    //        val (evens, odds) = stream.stream()
    //                .findFirst()
    //                .orElseGet {
    //                    Pair(emptySequence(),
    //                         emptySequence())
    //                }
    //        val evensList: List<Int> = evens.toList()
    //        val oddsList: List<Int> = odds.toList()
    //        Assertions.assertEquals(3,
    //                                evensList.size)
    //        Assertions.assertEquals(2,
    //                                oddsList.size)
    //        Assertions.assertEquals(0,
    //                                evensList[0])
    //        Assertions.assertEquals(1,
    //                                oddsList[0])
    //    }

}
