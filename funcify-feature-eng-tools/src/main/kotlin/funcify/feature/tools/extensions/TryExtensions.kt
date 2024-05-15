package funcify.feature.tools.extensions

import arrow.core.Option
import funcify.feature.tools.container.attempt.Failure
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import java.util.stream.Stream

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

    inline fun <reified S : Any, reified R : Any> Sequence<Try<S>>.tryFold(
        initial: R,
        crossinline accumulator: (R, S) -> R
    ): Try<R> {
        return this.fold(Try.success(initial)) { result: Try<R>, nextAttempt: Try<S> ->
            result.flatMap { r: R -> nextAttempt.map { s: S -> accumulator.invoke(r, s) } }
        }
    }

    inline fun <reified T : Any, reified R : Any> Sequence<T>.foldIntoTry(
        initial: R,
        crossinline accumulator: (R, T) -> R
    ): Try<R> {
        return this.fold(Try.success(initial)) { result: Try<R>, nextItem: T ->
            result.map { r: R -> accumulator.invoke(r, nextItem) }
        }
    }

    inline fun <reified S : Any, reified R : Any> Iterable<Try<S>>.tryFold(
        initial: R,
        crossinline accumulator: (R, S) -> R
    ): Try<R> {
        return this.asSequence().tryFold(initial, accumulator)
    }

    inline fun <reified T : Any, reified R : Any> Iterable<T>.foldIntoTry(
        initial: R,
        crossinline accumulator: (R, T) -> R
    ): Try<R> {
        return this.asSequence().foldIntoTry(initial, accumulator)
    }

    inline fun <reified S : Any, reified R : Any> Stream<Try<S>>.tryReduce(
        initial: R,
        crossinline accumulator: (R, S) -> R,
        crossinline combiner: (R, R) -> R
    ): Try<R> {
        return this.reduce(
            Try.success(initial),
            { result: Try<R>, nextAttempt: Try<S> ->
                result.flatMap { r: R -> nextAttempt.map { s: S -> accumulator.invoke(r, s) } }
            },
            { result1: Try<R>, result2: Try<R> ->
                result1.flatMap { r1: R -> result2.map { r2: R -> combiner.invoke(r1, r2) } }
            }
        )
    }

    inline fun <reified T : Any, reified R : Any> Stream<T>.reduceIntoTry(
        initial: R,
        crossinline accumulator: (R, T) -> R,
        crossinline combiner: (R, R) -> R
    ): Try<R> {
        return this.reduce(
            Try.success(initial),
            { result: Try<R>, nextItem: T ->
                result.map { r: R -> accumulator.invoke(r, nextItem) }
            },
            { result1: Try<R>, result2: Try<R> ->
                result1.flatMap { r1: R -> result2.map { r2: R -> combiner.invoke(r1, r2) } }
            }
        )
    }
}
