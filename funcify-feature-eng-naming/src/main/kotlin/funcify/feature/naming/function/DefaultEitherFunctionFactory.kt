package funcify.feature.naming.function


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
internal object DefaultEitherFunctionFactory {

    internal data class LeftResultFunction<L, R>(private val function: (R) -> L) : EitherFunction<L, R> {

        override fun <O> fold(leftResultFunction: ((R) -> L) -> O,
                              rightResultFunction: ((R) -> R) -> O): O {
            return leftResultFunction.invoke(function)
        }

    }

    internal data class RightResultFunction<L, R>(private val function: (R) -> R) : EitherFunction<L, R> {

        override fun <O> fold(leftResultFunction: ((R) -> L) -> O,
                              rightResultFunction: ((R) -> R) -> O): O {
            return rightResultFunction.invoke(function)
        }
    }

}