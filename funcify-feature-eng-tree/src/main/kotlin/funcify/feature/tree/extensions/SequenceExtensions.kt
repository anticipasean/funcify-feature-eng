package funcify.feature.tree.extensions

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*

internal object SequenceExtensions {

    fun <T, R> Sequence<T>.recurse(function: (T) -> Sequence<Either<T, R>>): Sequence<R> {
        var queue: Deque<Either<T, R>> = LinkedList<Either<T, R>>()
        this.forEach { t: T -> queue.offerLast(t.left()) }
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
