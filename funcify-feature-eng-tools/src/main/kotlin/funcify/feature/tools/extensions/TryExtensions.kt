package funcify.feature.tools.extensions

import arrow.core.Option
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try

object TryExtensions {

    fun <T : Any> T?.successIfNonNull(): Try<T> {
        return if (this == null) {
            Failure<T>(NoSuchElementException("result is null"))
        } else {
            Success<T>(this)
        }
    }

    fun <T : Any> T?.successIfNonNull(ifNull: () -> Throwable): Try<T> {
        return if (this == null) {
            Failure<T>(ifNull.invoke())
        } else {
            Success<T>(this)
        }
    }

    fun <S : Any> Option<S>.successIfDefined(): Try<S> {
        return this.successIfDefined { NoSuchElementException("result is not defined") }
    }

    fun <S : Any> Option<S>.successIfDefined(ifUndefined: () -> Throwable): Try<S> {
        return this.fold({ Failure<S>(ifUndefined.invoke()) }, { s -> Success<S>(s) })
    }

    inline fun <reified S : Any> Throwable.failure(): Try<S> {
        return Failure<S>(this)
    }

    inline fun <reified S : Any> Result<S>.toTry(): Try<S> {
        return this.fold({ s: S -> Success<S>(s) }, { t: Throwable -> Failure<S>(t) })
    }

    inline fun <reified S : Any, reified R : Any> Sequence<Try<S>>.foldTry(
        initial: R,
        crossinline accumulator: (R, S) -> R
    ): Try<R> {
        return this.fold(Try.success(initial)) { result: Try<R>, nextAttempt: Try<S> ->
            result.flatMap { r: R -> nextAttempt.map { s: S -> accumulator.invoke(r, s) } }
        }
    }

    inline fun <reified S : Any, reified R : Any> Iterable<Try<S>>.foldTry(
        initial: R,
        crossinline accumulator: (R, S) -> R
    ): Try<R> {
        return this.asSequence().foldTry(initial, accumulator)
    }
}
