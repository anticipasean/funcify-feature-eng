package funcify.naming.charseq.operation

import funcify.naming.charseq.operation.CharSeqFunctionContext.CharSeqIterableTerminatingFunctionContext
import funcify.naming.charseq.operation.CharSeqFunctionContext.CharSeqTerminatingFunctionContext


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
internal object DefaultCharSeqFunctionContextFactory {

    internal data class DefaultCharSeqTerminatingFunctionContext<CS, CSI>(override val function: (CS) -> CS) : CharSeqTerminatingFunctionContext<CS, CSI> {

        override fun mapCharSeq(charSeqMapper: (CS) -> CS,
                                charSeqIterMapper: (CSI) -> CS): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqTerminatingFunctionContext({ cs ->
                                                                charSeqMapper.invoke(function.invoke(cs))
                                                            })
        }

        override fun mapCharSeqIterable(charSeqMapper: (CS) -> CSI,
                                        charSeqIterMapper: (CSI) -> CSI): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqIterableTerminatingFunctionContext({ cs ->
                                                                        charSeqMapper.invoke(function.invoke(cs))
                                                                    })
        }

    }

    internal data class DefaultCharSeqIterableTerminatingFunctionContext<CS, CSI>(override val function: (CS) -> CSI) : CharSeqIterableTerminatingFunctionContext<CS, CSI> {

        override fun mapCharSeq(charSeqMapper: (CS) -> CS,
                                charSeqIterMapper: (CSI) -> CS): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqTerminatingFunctionContext({ cs ->
                                                                charSeqIterMapper.invoke(function.invoke(cs))
                                                            })
        }

        override fun mapCharSeqIterable(charSeqMapper: (CS) -> CSI,
                                        charSeqIterMapper: (CSI) -> CSI): CharSeqFunctionContext<CS, CSI> {
            return DefaultCharSeqIterableTerminatingFunctionContext({ cs ->
                                                                        charSeqIterMapper.invoke(function.invoke(cs))
                                                                    })
        }

    }

}