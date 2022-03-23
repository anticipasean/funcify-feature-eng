package funcify.naming.charseq.spliterator

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.stream.IntStream


/**
 *
 * @author smccarron
 * @created 3/23/22
 */
internal class SlidingListWindowMappingSpliteratorTest {

    @Test
    fun oddWindowSizeSmallerThanWindowSizeInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      1)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 3,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[0]
                                                                        3 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                0),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun oddWindowSizeSmallerThanWindowSizeInputRangeTest2() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      3)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 5,
                                                                windowMapper = { ints ->
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[0]
                                                                        3 -> ints[0]
                                                                        4 -> ints[0]
                                                                        5 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                0,
                                                0,
                                                1),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun tripleWindowMappingSpliteratorSmallerThanWindowInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      1)
                .boxed()
                .spliterator()

        val tripleSplitr = TripleWindowMappingSpliterator(inputSpliterator = sourceSpliterator)
        val resultList: MutableList<Triple<Int?, Int, Int?>> = mutableListOf()
        tripleSplitr.forEachRemaining { trip ->
            resultList.add(trip)
        }
        Assertions.assertEquals("{ (null, 0, 1), (0, 1, null) }",
                                resultList.joinToString(", ",
                                                        "{ ",
                                                        " }"),
                                "results do not match")

    }


    @Test
    fun tripleWindowMappingSpliteratorSameAsWindowSizeInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      2)
                .boxed()
                .spliterator()

        val tripleSplitr = TripleWindowMappingSpliterator(inputSpliterator = sourceSpliterator)
        val resultList: MutableList<Triple<Int?, Int, Int?>> = mutableListOf()
        tripleSplitr.forEachRemaining { trip ->
            resultList.add(trip)
        }
        Assertions.assertEquals("{ (null, 0, 1), (0, 1, 2), (1, 2, null) }",
                                resultList.joinToString(", ",
                                                        "{ ",
                                                        " }"),
                                "results do not match")

    }

    @Test
    fun pairWindowMappingSpliteratorSmallerThanWindowInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      0)
                .boxed()
                .spliterator()

        val pairSplitr = PairWindowMappingSpliterator(inputSpliterator = sourceSpliterator)
        val resultList: MutableList<Pair<Int?, Int?>> = mutableListOf()
        pairSplitr.forEachRemaining { trip ->
            resultList.add(trip)
        }
        Assertions.assertEquals("{ (null, 0), (0, null) }",
                                resultList.joinToString(", ",
                                                        "{ ",
                                                        " }"),
                                "results do not match")

    }

    @Test
    fun pairWindowMappingSpliteratorLargerThanWindowInputSizeOddRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      18)
                .boxed()
                .spliterator()

        val pairSplitr = PairWindowMappingSpliterator(inputSpliterator = sourceSpliterator)
        val resultList: MutableList<Pair<Int?, Int?>> = mutableListOf()
        pairSplitr.forEachRemaining { trip ->
            resultList.add(trip)
        }
        Assertions.assertEquals("""
            |{ (null, 0), 
            |(0, 1), 
            |(1, 2), 
            |(2, 3), 
            |(3, 4), 
            |(4, 5), 
            |(5, 6), 
            |(6, 7), 
            |(7, 8), 
            |(8, 9), 
            |(9, 10), 
            |(10, 11), 
            |(11, 12), 
            |(12, 13), 
            |(13, 14), 
            |(14, 15), 
            |(15, 16), 
            |(16, 17), 
            |(17, 18), 
            |(18, null) }
            """.trimMargin()
                                        .replace("\n",
                                                 ""),
                                resultList.joinToString(", ",
                                                        "{ ",
                                                        " }"),
                                "results do not match")

    }

    @Test
    fun pairWindowMappingSpliteratorSameAsWindowSizeInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      1)
                .boxed()
                .spliterator()

        val pairSplitr = PairWindowMappingSpliterator(inputSpliterator = sourceSpliterator)
        val resultList: MutableList<Pair<Int?, Int?>> = mutableListOf()
        pairSplitr.forEachRemaining { trip ->
            resultList.add(trip)
        }
        Assertions.assertEquals("{ (null, 0), (0, 1), (1, null) }",
                                resultList.joinToString(", ",
                                                        "{ ",
                                                        " }"),
                                "results do not match")

    }

    @Test
    fun oddWindowSizeSmallInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      4)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 3,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[0]
                                                                        3 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                0,
                                                1,
                                                2,
                                                3),
                                     resultList.toIntArray(),
                                     "results do not match")

    }


    @Test
    fun oddWindowSizeLargeInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      15)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 3,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[0]
                                                                        3 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                0,
                                                1,
                                                2,
                                                3,
                                                4,
                                                5,
                                                6,
                                                7,
                                                8,
                                                9,
                                                10,
                                                11,
                                                12,
                                                13,
                                                14),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun evenWindowSizeSmallInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      4)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 2,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                0,
                                                1,
                                                2,
                                                3,
                                                4),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun minimumWindowSizeSmallInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      4)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 1,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                1,
                                                2,
                                                3,
                                                4),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun minimumWindowSizeLargeInputRangeTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      18)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 1,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(0,
                                                1,
                                                2,
                                                3,
                                                4,
                                                5,
                                                6,
                                                7,
                                                8,
                                                9,
                                                10,
                                                11,
                                                12,
                                                13,
                                                14,
                                                15,
                                                16,
                                                17,
                                                18),
                                     resultList.toIntArray(),
                                     "results do not match")

    }

    @Test
    fun largerEvenWindowSizeLargeInputSetTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      18)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 4,
                                                                windowMapper = { ints -> //println("window: ${
                                                                    //    ints.asSequence()
                                                                    //            .joinToString(", ",
                                                                    //                          "{ ",
                                                                    //                          " }")
                                                                    //}")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[1]
                                                                        3 -> ints[1]
                                                                        4 -> ints[1]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(1,
                                                1,
                                                1,
                                                2,
                                                3,
                                                4,
                                                5,
                                                6,
                                                7,
                                                8,
                                                9,
                                                10,
                                                11,
                                                12,
                                                13,
                                                14,
                                                15,
                                                16,
                                                17,
                                                18),
                                     resultList.toIntArray(),
                                     "results do not match")
    }

    @Test
    fun largerOddWindowSizeLargeInputSetTest() {
        val sourceSpliterator = IntStream.rangeClosed(0,
                                                      18)
                .boxed()
                .spliterator()

        val slidingSplitr = SlidingListWindowMappingSpliterator(inputSpliterator = sourceSpliterator,
                                                                windowSize = 7,
                                                                windowMapper = { ints -> // println("window: ${
                                                                    //     ints.asSequence()
                                                                    //             .joinToString(", ",
                                                                    //                           "{ ",
                                                                    //                           " }")
                                                                    // }")
                                                                    when (ints.size) {
                                                                        1 -> ints[0]
                                                                        2 -> ints[1]
                                                                        3 -> ints[2]
                                                                        4 -> ints[3]
                                                                        5 -> ints[3]
                                                                        6 -> ints[3]
                                                                        7 -> ints[3]
                                                                        else -> -1
                                                                    }
                                                                })
        val resultList: MutableList<Int> = mutableListOf()
        slidingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertArrayEquals(intArrayOf(3,
                                                3,
                                                3,
                                                3,
                                                4,
                                                5,
                                                6,
                                                7,
                                                8,
                                                9,
                                                10,
                                                11,
                                                12,
                                                13,
                                                14,
                                                15,
                                                16,
                                                17,
                                                18),
                                     resultList.toIntArray(),
                                     "results do not match")
    }

}