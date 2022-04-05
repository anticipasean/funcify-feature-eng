package funcify.feature.naming.charseq.context

import funcify.feature.naming.function.EitherStreamFunction
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/28/22
 */
data class CharSequenceStreamFuncContext<I>(override val inputToCharSequenceTransformer: (I) -> Stream<CharSequence>,
                                            val streamFunction: EitherStreamFunction<Char, CharSequence> = EitherStreamFunction.ofRight { cs -> cs }) : CharSequenceOperationContext<I, Stream<Char>, Stream<CharSequence>> {

    fun update(function: (EitherStreamFunction<Char, CharSequence>) -> EitherStreamFunction<Char, CharSequence>): CharSequenceStreamFuncContext<I> {
        return CharSequenceStreamFuncContext(inputToCharSequenceTransformer = inputToCharSequenceTransformer,
                                             streamFunction = function.invoke(streamFunction))
    }
}
