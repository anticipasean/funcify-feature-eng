package funcify.feature.tools.container.attempt

data class Success<S>(val successObject: S) : Try<S> {

    override fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R {
        return successHandler.invoke(successObject)
    }
}
