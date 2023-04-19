package funcify.feature.tree.extensions

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none

object OptionExtensions {

    fun <T, R> Option<T>?.recurse(function: (T) -> Option<Either<T, R>>): Option<R> {
        val startValue: Option<T> = this ?: none()
        if (startValue.isEmpty()) {
            return none()
        }
        val nextOptionHolder: Array<Option<Either<T, R>>> =
            arrayOf(startValue.map { t: T -> t.left() })
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
}
