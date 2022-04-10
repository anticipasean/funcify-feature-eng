package funcify.feature.tools.control

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.core.some
import java.util.Deque
import java.util.LinkedList
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
object TraversalFunctions {

    fun <T, R> recurseWithOption(startValue: T,
                                 function: (T) -> Option<Either<T, R>>): Option<R> {
        val nextOptionHolder: Array<Option<Either<T, R>>> = arrayOf(startValue.left()
                                                                            .some())
        var continueLoop = true
        do {
            continueLoop = nextOptionHolder[0].fold({ false },
                                                    { either: Either<T, R> ->
                                                        either.fold({ t: T ->
                                                                        nextOptionHolder[0] = function.invoke(t)
                                                                        true
                                                                    },
                                                                    { _ -> false })
                                                    })
        } while (continueLoop)
        return nextOptionHolder[0].mapNotNull { either: Either<T, R> -> either.orNull() }
    }

    fun <T, R> recurseWithStream(startValue: T,
                                 function: (T) -> Stream<Either<T, R>>): Stream<R> {
        if (startValue == null) {
            return Stream.empty()
        }
        var queue: Deque<Either<T, R>> = LinkedList<Either<T, R>>().apply { add(startValue.left()) }
        val continueLoopFlagHolder: Array<Boolean> = arrayOf(true)
        do {
            queue = queue.stream()
                    .flatMap { either: Either<T, R> ->
                        either.fold({ t: T ->
                                        continueLoopFlagHolder[0] = true
                                        function.invoke(t)
                                    },
                                    { r: R ->
                                        continueLoopFlagHolder[0] = false
                                        Stream.of(r.right())
                                    })
                    }
                    .collect({ LinkedList<Either<T, R>>() },
                             { q, e -> q.apply { offerLast(e) } },
                             { q1, q2 -> q1.addAll(q2) })
        } while (continueLoopFlagHolder[0])
        return queue.stream()
                .flatMap { either: Either<T, R> ->
                    either.fold({ _: T -> Stream.empty() },
                                { r: R -> Stream.of(r) })
                }
    }


}