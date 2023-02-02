package funcify.feature.tools.container.attempt

data class Failure<S>(val throwable: Throwable) : Try<S> {

    override fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R {
        return failureHandler.invoke(throwable)
    }
}
