package funcify.naming.function

import funcify.naming.function.FunctionExtensions.andThen
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/28/22
 */
sealed interface EitherStreamFunction<L, R> {

    companion object {

        data class LeftStreamFunction<L, R>(val baseStreamFunction: (Stream<R>) -> Stream<R>,
                                            val leftFunction: (R) -> Stream<L>) : EitherStreamFunction<L, R> {
            override fun <O> fold(left: ((Stream<R>) -> Stream<R>, (R) -> Stream<L>) -> O,
                                  right: ((Stream<R>) -> Stream<R>) -> O): O {
                return left.invoke(baseStreamFunction,
                                   leftFunction)
            }

        }

        data class RightStreamFunction<L, R>(val baseStreamFunction: (Stream<R>) -> Stream<R>) : EitherStreamFunction<L, R> {

            override fun <O> fold(left: ((Stream<R>) -> Stream<R>, (R) -> Stream<L>) -> O,
                                  right: ((Stream<R>) -> Stream<R>) -> O): O {
                return right.invoke(baseStreamFunction)
            }

        }

        fun <L, R> ofRight(baseStreamFunction: (Stream<R>) -> Stream<R>): EitherStreamFunction<L, R> {
            return RightStreamFunction<L, R>(baseStreamFunction = baseStreamFunction)
        }

        fun <L, R> ofLeft(baseStreamFunction: (Stream<R>) -> Stream<R>,
                          leftMapper: (R) -> Stream<L>): EitherStreamFunction<L, R> {
            return LeftStreamFunction<L, R>(baseStreamFunction = baseStreamFunction,
                                            leftFunction = leftMapper)
        }
    }

    fun isLeft(): Boolean {
        return fold({ _, _ -> true },
                    { _ -> false })
    }

    fun isRight(): Boolean {
        return fold({ _, _ -> false },
                    { _ -> true })
    }

    fun mapLeft(function: (Stream<L>) -> Stream<L>): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, leftMapper: (R) -> Stream<L> ->
                        ofLeft(baseStreamFunction = base,
                               leftMapper = leftMapper.andThen(function))
                    },
                    { base: (Stream<R>) -> Stream<R> -> // Nothing happens in this case
                        ofRight(baseStreamFunction = base)
                    })
    }

    fun mapRight(function: (Stream<R>) -> Stream<R>): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, leftMapper: (R) -> Stream<L> ->
                        ofLeft(baseStreamFunction = base,
                               leftMapper = leftMapper)
                    },
                    { base: (Stream<R>) -> Stream<R> ->
                        ofRight(baseStreamFunction = base.andThen(function))
                    })
    }

    fun mapRightToLeft(leftMapper: (R) -> Stream<L>): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, left: (R) -> Stream<L> ->
                        ofLeft(baseStreamFunction = base,
                               leftMapper = left)
                    },
                    { base: (Stream<R>) -> Stream<R> ->
                        ofLeft(baseStreamFunction = base,
                               leftMapper = leftMapper)
                    })
    }

    fun mapLeftToRight(rightMapper: (Stream<L>) -> R): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, leftMapper: (R) -> Stream<L> ->
                        ofRight(baseStreamFunction = base.andThen { stream: Stream<R> ->
                            stream.map { r -> rightMapper.invoke(leftMapper.invoke(r)) }
                        })
                    },
                    { base: (Stream<R>) -> Stream<R> ->
                        ofRight(baseStreamFunction = base)
                    })
    }

    fun flatMapLeft(mapper: ((Stream<R>) -> Stream<R>, (R) -> Stream<L>) -> EitherStreamFunction<L, R>): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, leftMapper: (R) -> Stream<L> ->
                        mapper.invoke(base,
                                      leftMapper)
                    },
                    { base: (Stream<R>) -> Stream<R> ->
                        ofRight(base)
                    })
    }

    fun flatMapRight(mapper: ((Stream<R>) -> Stream<R>) -> EitherStreamFunction<L, R>): EitherStreamFunction<L, R> {
        return fold({ base: (Stream<R>) -> Stream<R>, leftMapper: (R) -> Stream<L> ->
                        ofLeft(base,
                               leftMapper)
                    },
                    { base: (Stream<R>) -> Stream<R> ->
                        mapper.invoke(base)
                    })
    }

    fun <O> fold(left: ((Stream<R>) -> Stream<R>, (R) -> Stream<L>) -> O,
                 right: ((Stream<R>) -> Stream<R>) -> O): O

}