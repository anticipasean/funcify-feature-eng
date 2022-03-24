package funcify.naming.charseq.operation

import arrow.core.andThen


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
sealed interface CharSeqFunctionContext<CS, CSI> {

    companion object {

        fun <CS, CSI> ofCharSeqFunction(function: (CSI) -> CS): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqFunctionContextFactory.DefaultCharSeqTerminatingFunctionContext(function)
        }

        fun <CS, CSI> ofCharSeqIterableFunction(function: (CSI) -> CSI): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqFunctionContextFactory.DefaultCharSeqIterableTerminatingFunctionContext(function)
        }

    }

    fun currentlyTerminatesInCharSequence(): Boolean {
        return fold({ csf -> true },
                    { csif -> false })
    }

    fun currentlyTerminatesInCharSequenceIterable(): Boolean {
        return fold({ csf -> false },
                    { csif -> true })
    }

    fun mapCharSeq(charSeqMapper: (CS) -> CS,
                   charSeqIterMapper: (CSI) -> CS): CharSeqFunctionContext<CS, CSI> {
        return fold({ csTermFunc ->
                        ofCharSeqFunction(csTermFunc.andThen(charSeqMapper))
                    },
                    { csiTermFunc ->
                        ofCharSeqFunction(csiTermFunc.andThen(charSeqIterMapper))
                    })
    }

    fun mapCharSeqIterable(charSeqMapper: (CS) -> CSI,
                           charSeqIterMapper: (CSI) -> CSI): CharSeqFunctionContext<CS, CSI> {
        return fold({ csTermFunc ->
                        ofCharSeqIterableFunction(csTermFunc.andThen(charSeqMapper))
                    },
                    { csiTermFunc ->
                        ofCharSeqIterableFunction(csiTermFunc.andThen(charSeqIterMapper))
                    })
    }

    fun <R> fold(charSeqFunction: ((CSI) -> CS) -> R,
                 charSeqIterableFunction: ((CSI) -> CSI) -> R): R


    interface CharSeqTerminatingFunctionContext<CS, CSI> : CharSeqFunctionContext<CS, CSI> {

        val function: (CSI) -> CS

        override fun <R> fold(charSeqFunction: ((CSI) -> CS) -> R,
                              charSeqIterableFunction: ((CSI) -> CSI) -> R): R {
            return charSeqFunction.invoke(function)
        }
    }

    interface CharSeqIterableTerminatingFunctionContext<CS, CSI> : CharSeqFunctionContext<CS, CSI> {

        val function: (CSI) -> CSI

        override fun <R> fold(charSeqFunction: ((CSI) -> CS) -> R,
                              charSeqIterableFunction: ((CSI) -> CSI) -> R): R {
            return charSeqIterableFunction.invoke(function)
        }
    }


}