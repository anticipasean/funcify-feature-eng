package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
sealed interface CharSeqFunctionContext<CS, CSI> {

    companion object {

        fun <CS, CSI> ofCharSeqFunction(function: (CS) -> CS): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqFunctionContextFactory.DefaultCharSeqTerminatingFunctionContext(function)
        }

        fun <CS, CSI> ofCharSeqIterableFunction(function: (CS) -> CSI): CharSeqFunctionContext<CS, CSI> {
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
                   charSeqIterMapper: (CSI) -> CS): CharSeqFunctionContext<CS, CSI>

    fun mapCharSeqIterable(charSeqMapper: (CS) -> CSI,
                           charSeqIterMapper: (CSI) -> CSI): CharSeqFunctionContext<CS, CSI>

    fun <R> fold(charSeqFunction: ((CS) -> CS) -> R,
                 charSeqIterableFunction: ((CS) -> CSI) -> R): R


    interface CharSeqTerminatingFunctionContext<CS, CSI> : CharSeqFunctionContext<CS, CSI> {

        val function: (CS) -> CS

        override fun <R> fold(charSeqFunction: ((CS) -> CS) -> R,
                              charSeqIterableFunction: ((CS) -> CSI) -> R): R {
            return charSeqFunction.invoke(function)
        }
    }

    interface CharSeqIterableTerminatingFunctionContext<CS, CSI> : CharSeqFunctionContext<CS, CSI> {

        val function: (CS) -> CSI

        override fun <R> fold(charSeqFunction: ((CS) -> CS) -> R,
                              charSeqIterableFunction: ((CS) -> CSI) -> R): R {
            return charSeqIterableFunction.invoke(function)
        }
    }


}