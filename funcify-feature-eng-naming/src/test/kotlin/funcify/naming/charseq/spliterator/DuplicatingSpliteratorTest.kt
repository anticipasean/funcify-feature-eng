package funcify.naming.charseq.spliterator

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.stream.IntStream


/**
 *
 * @author smccarron
 * @created 3/24/22
 */
internal class DuplicatingSpliteratorTest {

    @Test
    fun firstThenSecondDuplicateSizeTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
    }

    @Test
    fun firstThenSecondDuplicateContentsTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(0,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[10])
        resultList.clear()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(0,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[10])
    }

    @Test
    fun secondThenFirstDuplicateSizeTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
    }

    @Test
    fun secondThenFirstDuplicateContentsTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(0,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[10])
        resultList.clear()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(0,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[10])
    }

    @Test
    fun multipleDuplicatesThenFirstDuplicateSizeTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr1 = duplicatingSplitr.duplicate()
        val duplicateSplitr2 = duplicatingSplitr.duplicate()
        val duplicateSplitr3 = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicateSplitr1.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr2.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr3.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
    }

    @Test
    fun firstDuplicateThenMultiplesSizeTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr1 = duplicatingSplitr.duplicate()
        val duplicateSplitr2 = duplicatingSplitr.duplicate()
        val duplicateSplitr3 = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr1.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr2.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
        resultList.clear()
        duplicateSplitr3.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(11,
                                resultList.size)
    }


    @Test
    fun duplicateOnSourceLaterContentsTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val halfSize = (inputSplitr.estimateSize() shr 1).toInt()
        var advanceCounter: Int = 0
        while (advanceCounter < halfSize && inputSplitr.tryAdvance { _ ->

                }) {
            advanceCounter++
        }
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        val duplicateSplitr = duplicatingSplitr.duplicate()

        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(5,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[5])
        resultList.clear()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(5,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[5])
    }

    @Test
    fun duplicateHalfwayContentsTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val halfSize = (inputSplitr.estimateSize() shr 1).toInt()
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        var advanceCounter: Int = 0
        while (advanceCounter < halfSize && duplicatingSplitr.tryAdvance { _ ->

                }) {
            advanceCounter++
        }
        val duplicateSplitr = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(6,
                                resultList.size)
        Assertions.assertEquals(5,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[5])
        resultList.clear()
        duplicateSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(6,
                                resultList.size)
        Assertions.assertEquals(5,
                                resultList[0])
        Assertions.assertEquals(10,
                                resultList[5])
    }

    @Test
    fun multipleStaggeredDuplicatesContentsTest() {
        val inputSplitr = IntStream.rangeClosed(0,
                                                10)
                .boxed()
                .spliterator()
        val stepSize: Int = 2
        val duplicatingSplitr = DuplicatingSpliterator(inputSplitr)
        var advanceCounter: Int = 0
        while (advanceCounter < stepSize && duplicatingSplitr.tryAdvance { _ ->

                }) {
            advanceCounter++
        }
        val duplicateSplitr1 = duplicatingSplitr.duplicate()
        advanceCounter = 0
        while (advanceCounter < stepSize && duplicatingSplitr.tryAdvance { _ ->

                }) {
            advanceCounter++
        }
        val duplicateSplitr2 = duplicatingSplitr.duplicate()
        advanceCounter = 0
        while (advanceCounter < stepSize && duplicatingSplitr.tryAdvance { _ ->

                }) {
            advanceCounter++
        }
        val duplicateSplitr3 = duplicatingSplitr.duplicate()
        val resultList: MutableList<Int> = mutableListOf()
        duplicatingSplitr.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(5,
                                resultList.size)
        resultList.clear()
        duplicateSplitr1.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(9,
                                resultList.size)
        resultList.clear()
        duplicateSplitr2.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(7,
                                resultList.size)
        resultList.clear()
        duplicateSplitr3.forEachRemaining { i ->
            resultList.add(i)
        }
        Assertions.assertEquals(5,
                                resultList.size)
        resultList.clear()
    }

}