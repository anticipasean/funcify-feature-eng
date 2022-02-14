package funcify.container.attempt

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal object TryFactory {

    data class Success<S>(val successObject: S) : Try<S> {

        override fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R {
            return successHandler.invoke(successObject)
        }
    }


    data class Failure<S>(val throwable: Throwable) : Try<S> {

        override fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R {
            return failureHandler.invoke(throwable)
        }
    }

}