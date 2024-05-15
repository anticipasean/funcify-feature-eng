package funcify.feature.tree.extensions

import arrow.core.Either

internal object EitherExtensions {

    fun <L, R> Either<L, R>.recurse(function: (L) -> Either<L, R>): R {
        if (this is Either.Right<R>) {
            return this.value
        }
        var continueLoop: Boolean = false
        val nextEitherHolder: Array<Either<L, R>> = arrayOf(this)
        do {
            continueLoop =
                nextEitherHolder[0].fold(
                    { l: L ->
                        nextEitherHolder[0] = function.invoke(l)
                        true
                    },
                    { false }
                )
        } while (continueLoop)
        return nextEitherHolder[0].orNull()!!
    }
}
