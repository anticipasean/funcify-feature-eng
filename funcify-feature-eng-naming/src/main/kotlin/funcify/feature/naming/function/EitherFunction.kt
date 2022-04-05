package funcify.feature.naming.function

import funcify.feature.naming.function.FunctionExtensions.andThen


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
sealed interface EitherFunction<L, R> {

    companion object {

        fun <L, R> ofLeftResult(function: (R) -> L): EitherFunction<L, R> {
            return DefaultEitherFunctionFactory.LeftResultFunction(function)
        }

        fun <L, R> ofRightResult(function: (R) -> R): EitherFunction<L, R> {
            return DefaultEitherFunctionFactory.RightResultFunction(function)
        }

    }

    fun yieldsLeftResult(): Boolean {
        return fold({ _ -> true },
                    { _ -> false })
    }

    fun yieldsRightResult(): Boolean {
        return fold({ _ -> false },
                    { _ -> true })
    }

    fun mapToLeftResult(leftMapper: (L) -> L,
                        rightMapper: (R) -> L): EitherFunction<L, R> {
        return fold({ leftFunction ->
                        ofLeftResult(leftFunction.andThen(leftMapper))
                    },
                    { rightFunction ->
                        ofLeftResult(rightFunction.andThen(rightMapper))
                    })
    }

    fun mapToRightResult(leftMapper: (L) -> R,
                         rightMapper: (R) -> R): EitherFunction<L, R> {
        return fold({ leftFunction ->
                        ofRightResult(leftFunction.andThen(leftMapper))
                    },
                    { rightFunction ->
                        ofRightResult(rightFunction.andThen(rightMapper))
                    })
    }

    fun <O> fold(leftResultFunction: ((R) -> L) -> O,
                 rightResultFunction: ((R) -> R) -> O): O

}