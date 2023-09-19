package funcify.feature.tools.control

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import java.util.*
import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 4/8/22
 */
object TraversalFunctions {

    fun <T, R> recurseWithOption(startValue: T, function: (T) -> Option<Either<T, R>>): Option<R> {
        if (startValue == null) {
            return none()
        }
        val nextOptionHolder: Array<Option<Either<T, R>>> = arrayOf(startValue.left().some())
        var continueLoop: Boolean
        do {
            continueLoop =
                nextOptionHolder[0].fold(
                    { false },
                    { either: Either<T, R> ->
                        either.fold(
                            { t: T ->
                                nextOptionHolder[0] = function.invoke(t)
                                true
                            },
                            { _ -> false }
                        )
                    }
                )
        } while (continueLoop)
        return nextOptionHolder[0].mapNotNull { either: Either<T, R> -> either.orNull() }
    }

    fun <T, R> recurseWithStream(startValue: T, function: (T) -> Stream<out Either<T, R>>): Stream<out R> {
        if (startValue == null) {
            return Stream.empty()
        }
        var queue: Deque<Either<T, R>> = LinkedList<Either<T, R>>().apply { add(startValue.left()) }
        val continueLoopFlagHolder: Array<Boolean> = arrayOf(true)
        do {
            queue =
                queue
                    .stream()
                    .flatMap { either: Either<T, R> ->
                        either.fold(
                            { t: T -> function.invoke(t) },
                            { r: R ->
                                when (r) {
                                    null -> Stream.empty()
                                    else -> Stream.of(r.right())
                                }
                            }
                        )
                    }
                    .collect(
                        {
                            continueLoopFlagHolder[0] = false
                            LinkedList<Either<T, R>>()
                        },
                        { q, e ->
                            if (e.isLeft() && !continueLoopFlagHolder[0]) {
                                continueLoopFlagHolder[0] = true
                            }
                            q.apply { offerLast(e) }
                        },
                        { q1, q2 -> q1.addAll(q2) }
                    )
        } while (continueLoopFlagHolder[0])
        return queue.stream().flatMap { either: Either<T, R> ->
            either.fold(
                { _: T -> Stream.empty() },
                { r: R ->
                    when (r) {
                        null -> Stream.empty()
                        else -> Stream.of(r)
                    }
                }
            )
        }
    }

    fun <T, R> recurseWithSequence(
        startValue: T,
        function: (T) -> Sequence<Either<T, R>>
    ): Sequence<R> {
        if (startValue == null) {
            return emptySequence()
        }
        var queue: Deque<Either<T, R>> = LinkedList<Either<T, R>>().apply { add(startValue.left()) }
        val continueLoopFlagHolder: Array<Boolean> = arrayOf(true)
        do {
            queue =
                queue
                    .asSequence()
                    .flatMap { either: Either<T, R> ->
                        either.fold(
                            { t: T -> function.invoke(t) },
                            { r: R ->
                                when (r) {
                                    null -> emptySequence()
                                    else -> sequenceOf(r.right())
                                }
                            }
                        )
                    }
                    .fold(LinkedList<Either<T, R>>().apply { continueLoopFlagHolder[0] = false }) {
                        q,
                        e ->
                        if (e.isLeft() && !continueLoopFlagHolder[0]) {
                            continueLoopFlagHolder[0] = true
                        }
                        q.apply { offerLast(e) }
                    }
        } while (continueLoopFlagHolder[0])
        return queue.asSequence().flatMap { either: Either<T, R> ->
            either.fold(
                { _: T -> emptySequence() },
                { r: R ->
                    when (r) {
                        null -> emptySequence()
                        else -> sequenceOf(r)
                    }
                }
            )
        }
    }
}
