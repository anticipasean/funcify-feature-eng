package funcify.feature.tools.container.attempt

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TryTest {

    @Test
    fun attemptFilterInstanceOfTest() {
        val message: String = "Hello"
        val csTry =
            Try.success(message).filterInstanceOf<CharSequence>().map { cs: CharSequence ->
                cs.get(0)
            }
        Assertions.assertEquals('H', csTry.orNull())
        val inputMap = mutableMapOf(message to "World")
        val listTry =
            Try.success(inputMap).filterInstanceOf<List<String>>().map { strList -> strList.get(0) }
        Assertions.assertInstanceOf(
            IllegalArgumentException::class.java,
            listTry.getFailure().orNull()
        )
        Assertions.assertTrue(
            listTry
                .getFailure()
                .map { t -> t.message }
                .map { msg -> msg?.contains(Regex("is not instance of")) ?: false }
                .getOrElse { false },
            "does not assert what type input fails to be an instance of"
        )
        val nullInputAttempt: Try<List<String>> =
            Assertions.assertDoesNotThrow<Try<List<String>>> {
                Try.success(inputMap).map { map -> null }.filterInstanceOf<List<String>>()
            }
        Assertions.assertTrue(
            nullInputAttempt
                .getFailure()
                .filterIsInstance<NoSuchElementException>()
                .map { t -> t.message }
                .map { msg -> msg?.contains(Regex("result is null")) ?: false }
                .getOrElse { false },
            """is not of expected default null output 
                    |throwable type and does not assert result 
                    |of map function is null""".flatten()
        )
    }
}
