package funcify.naming.charseq.spliterator

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Spliterator
import java.util.stream.IntStream


/**
 *
 * @author smccarron
 * @created 3/29/22
 */
internal class MappingWithIndexSpliteratorTest {

    @Test
    fun basicMappingWithIndexTest() {
        val resultList: MutableList<String> = mutableListOf()
        MappingWithIndexSpliterator(sourceSpliterator = IntStream.rangeClosed('a'.code,
                                                                              'i'.code)
                .mapToObj { i: Int -> i.toChar() }
                .spliterator(),
                                    mapper = { i: Int, c: Char ->
                                        "${i}: ${c.uppercaseChar()}"
                                    }).forEachRemaining { s: String ->
            resultList.add(s)
        }
        Assertions.assertEquals("0: A",
                                resultList[0])
    }


    @Test
    fun splitMappingWithIndexTest() {
        val resultList: MutableList<String> = mutableListOf()
        val intRange: IntRange = 'a'.code..'i'.code
        val sourceSpliterator = intRange.map { i: Int -> i.toChar() }
                .toList()
                .spliterator()
        Assertions.assertTrue((sourceSpliterator.characteristics() and Spliterator.SIZED) == Spliterator.SIZED)
        Assertions.assertEquals(intRange.count()
                                        .toLong(),
                                sourceSpliterator.estimateSize())
        val spliterator = MappingWithIndexSpliterator(sourceSpliterator = sourceSpliterator,
                                                      mapper = { i: Int, c: Char ->
                                                          "${i}: ${c.uppercaseChar()}"
                                                      })
        val frontRangeSplit = spliterator.trySplit()
        Assertions.assertNotNull(frontRangeSplit)
        spliterator.forEachRemaining { s: String ->
            resultList.add(s)
        }
        Assertions.assertEquals("4: E",
                                resultList[0])
        Assertions.assertEquals("8: I",
                                resultList.last())
        resultList.clear()
        val advanceStatus = frontRangeSplit?.tryAdvance { s -> resultList.add(s) }
                            ?: false
        Assertions.assertTrue(advanceStatus)
        frontRangeSplit?.forEachRemaining { s ->
            resultList.add(s)
        }
        Assertions.assertEquals("0: A",
                                resultList[0])
        Assertions.assertEquals("3: D",
                                resultList.last())
    }
}