package funcify.feature.tools.extensions

import arrow.core.Option
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.TryFactory

object TryExtensions {

    fun <T : Any> T?.successIfNonNull(): Try<T> {
        return if (this == null) {
            TryFactory.Failure<T>(NoSuchElementException("result is null"))
        } else {
            TryFactory.Success<T>(this)
        }
    }

    fun <S : Any> Option<S>.successIfDefined(): Try<S> {
        return this.successIfDefined { NoSuchElementException("result is not defined") }
    }

    fun <S : Any> Option<S>.successIfDefined(ifUndefined: () -> Throwable): Try<S> {
        return this.fold(
            { TryFactory.Failure<S>(ifUndefined.invoke()) },
            { s -> TryFactory.Success<S>(s) }
        )
    }

    inline fun <reified S> Throwable.failure(): Try<S> {
        return TryFactory.Failure<S>(this)
    }
}
