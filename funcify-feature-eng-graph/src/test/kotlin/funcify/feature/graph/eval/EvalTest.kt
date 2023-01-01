package funcify.feature.graph.eval

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 2022-12-31
 */
class EvalTest {

    @Test
    fun evaluateComputedOnceTwiceWithSameResultTest() {
        val computedTime: Eval<Long> = Eval.computeOnce { System.currentTimeMillis() }
        val addedTimeMs = 5000L
        val updatedTime1 = computedTime.flatMap { time -> Eval.done(time + addedTimeMs) }.invoke()
        try {
            Thread.sleep(500)
        } catch (t: Throwable) {
            // ignore
        }
        val updatedTime2 = computedTime.flatMap { time -> Eval.done(time + addedTimeMs) }.invoke()
        Assertions.assertEquals(updatedTime1, updatedTime2, "not the same updated_time")
    }

    @Test
    fun evaluateComputedAlwaysTwiceWithDifferentResultsTest() {
        val computedTime: Eval<Long> = Eval.computeAlways { System.currentTimeMillis() }
        val addedTimeMs = 5000L
        val updatedTime1 = computedTime.flatMap { time -> Eval.done(time + addedTimeMs) }.invoke()
        try {
            Thread.sleep(500)
        } catch (t: Throwable) {
            // ignore
        }
        val updatedTime2 = computedTime.flatMap { time -> Eval.done(time + addedTimeMs) }.invoke()
        Assertions.assertNotEquals(updatedTime1, updatedTime2, "is the same updated_time")
    }

    @Test
    fun evaluateComputedOnceIsDoneStatus() {
        val computedTime: Eval<Long> = Eval.computeOnce { System.currentTimeMillis() }
        Assertions.assertFalse(computedTime.isDone(), "computed_once is marked done")
        computedTime.invoke()
        Assertions.assertTrue(computedTime.isDone(), "computed_once not marked done")
    }

    /**
     * The following test fixtures and test spec follow/use the same logic as those for arrow-kt's Eval
     * [arrow-kt.EvalTest](https://github.com/arrow-kt/arrow/blob/main/arrow-libs/core/arrow-core/src/commonTest/kotlin/arrow/core/EvalTest.kt)
     */
    private data class SideEffect(var counter: Int = 0) {
        fun increment() {
            counter++
        }
    }

    private fun recursiveCountComputation(limit: Int, sideEffect: SideEffect): (Int) -> Eval<Int> {
        return { num ->
            if (num <= limit) {
                sideEffect.increment()
                Eval.defer { recursiveCountComputation(limit, sideEffect).invoke(num + 1) }
            } else {
                Eval.done(-1)
            }
        }
    }

    @Test
    fun flatMapStackSafeTest() {
        /**
         * JVM limit per
         * [stackSafeIteration](https://github.com/arrow-kt/arrow/blob/main/arrow-libs/core/arrow-core/src/commonTest/kotlin/arrow/core/test/Platform.kt)
         */
        val limit = 500_000
        val sideEffect = SideEffect()
        val flatMappedComputation: Eval<Int> =
            try {
                Eval.done(0).flatMap(recursiveCountComputation(limit, sideEffect))
            } catch (t: Throwable) {
                Assertions.fail("flatMap likely isn't stack-safe", t)
            }
        Assertions.assertEquals(
            sideEffect.counter,
            0,
            "side_effect.counter not set to default value"
        )
        Assertions.assertEquals(
            flatMappedComputation.invoke(),
            -1,
            "flatmapped_computation.get value did not reach expected result value"
        )
        Assertions.assertEquals(
            sideEffect.counter,
            limit + 1,
            "side_effect.counter did not reach stack limit"
        )
    }
}
