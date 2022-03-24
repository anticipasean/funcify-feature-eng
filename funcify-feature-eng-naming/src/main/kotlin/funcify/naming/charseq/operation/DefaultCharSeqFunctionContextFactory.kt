package funcify.naming.charseq.operation

import funcify.naming.charseq.operation.CharSeqFunctionContext.CharSeqIterableTerminatingFunctionContext
import funcify.naming.charseq.operation.CharSeqFunctionContext.CharSeqTerminatingFunctionContext


/**
 *
 * @author smccarron
 * @created 3/20/22
 */
internal object DefaultCharSeqFunctionContextFactory {

    internal data class DefaultCharSeqTerminatingFunctionContext<CS, CSI>(override val function: (CSI) -> CS) : CharSeqTerminatingFunctionContext<CS, CSI> {


    }

    internal data class DefaultCharSeqIterableTerminatingFunctionContext<CS, CSI>(override val function: (CSI) -> CSI) : CharSeqIterableTerminatingFunctionContext<CS, CSI> {


    }

}