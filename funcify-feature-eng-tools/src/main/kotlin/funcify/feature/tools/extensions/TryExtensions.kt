package funcify.feature.tools.extensions

import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.TryFactory

object TryExtensions {

    fun <T : Any?> T.success(): Try<T> {
        return if (this == null) {
            TryFactory.Failure<T>(NoSuchElementException("result is null"))
        } else {
            TryFactory.Success<T>(this)
        }
    }

    inline fun <reified S> Throwable.failure(): Try<S> {
        return TryFactory.Failure<S>(this)
    }
}
