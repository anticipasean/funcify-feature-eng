package funcify.feature.tools.extensions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.tools.extensions.SequenceExtensions.singleValueMapCombinationsFromEntries
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.AbstractMap.SimpleImmutableEntry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2023-09-23
 */
class SequenceExtensionsTest {

    @Test
    fun mapEntryCombinationsTest1() {
        val results: List<Map<String, Int>> =
            mapOf("a" to listOf<Int>(1, 2, 3), "b" to listOf<Int>(4, 5, 6), "c" to listOf<Int>(7))
                .asSequence()
                .flatMap { (s: String, ns: List<Int>) ->
                    ns.asSequence().map { i: Int -> SimpleImmutableEntry(s, i) }
                }
                .singleValueMapCombinationsFromEntries()
                .toList()
        Assertions.assertEquals(9, results.size) { "results.size unexpected" }
        Assertions.assertTrue(results.all { m: Map<String, Int> -> m.size == 3 }) {
            "not all result maps are expected size"
        }
        Assertions.assertTrue(
            results.all { m: Map<String, Int> ->
                sequenceOf("a", "b", "c").all { s: String -> m.containsKey(s) }
            }
        ) {
            "not all result maps contain expected key set"
        }
        val expectedResultsAsJSON: String =
            """
            |[
            |  {
            |    "a": 1,
            |    "b": 4,
            |    "c": 7
            |  },
            |  {
            |    "a": 1,
            |    "b": 5,
            |    "c": 7
            |  },
            |  {
            |    "a": 1,
            |    "b": 6,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 4,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 5,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 6,
            |    "c": 7
            |  },
            |  {
            |    "a": 3,
            |    "b": 4,
            |    "c": 7
            |  },
            |  {
            |    "a": 3,
            |    "b": 5,
            |    "c": 7
            |  },
            |  {
            |    "a": 3,
            |    "b": 6,
            |    "c": 7
            |  }
            |]
        """
                .flatten()
        val expectedResults: List<Map<String, Int>> =
            Assertions.assertDoesNotThrow<List<Map<String, Int>>> {
                ObjectMapper().readValue<List<Map<String, Int>>>(expectedResultsAsJSON)
            }
        Assertions.assertEquals(expectedResults, results)
    }

    @Test
    fun mapEntryCombinationsTest2() {
        val results: List<Map<String, Int>> =
            mapOf("a" to listOf<Int>(1, 2), "b" to listOf<Int>(3, 4, 5, 6), "c" to listOf<Int>(7))
                .asSequence()
                .flatMap { (s: String, ns: List<Int>) ->
                    ns.asSequence().map { i: Int -> SimpleImmutableEntry(s, i) }
                }
                .singleValueMapCombinationsFromEntries()
                .toList()
        Assertions.assertEquals(8, results.size) { "results.size unexpected" }
        Assertions.assertTrue(results.all { m: Map<String, Int> -> m.size == 3 }) {
            "not all result maps are expected size"
        }
        Assertions.assertTrue(
            results.all { m: Map<String, Int> ->
                sequenceOf("a", "b", "c").all { s: String -> m.containsKey(s) }
            }
        ) {
            "not all result maps contain expected key set"
        }
        val expectedResultsAsJSON: String =
            """
            |[
            |  {
            |    "a": 1,
            |    "b": 3,
            |    "c": 7
            |  },
            |  {
            |    "a": 1,
            |    "b": 4,
            |    "c": 7
            |  },
            |  {
            |    "a": 1,
            |    "b": 5,
            |    "c": 7
            |  },
            |  {
            |    "a": 1,
            |    "b": 6,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 3,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 4,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 5,
            |    "c": 7
            |  },
            |  {
            |    "a": 2,
            |    "b": 6,
            |    "c": 7
            |  }
            |]
        """
                .flatten()
        val expectedResults: List<Map<String, Int>> =
            Assertions.assertDoesNotThrow<List<Map<String, Int>>> {
                ObjectMapper().readValue<List<Map<String, Int>>>(expectedResultsAsJSON)
            }
        Assertions.assertEquals(expectedResults, results)
    }
}
