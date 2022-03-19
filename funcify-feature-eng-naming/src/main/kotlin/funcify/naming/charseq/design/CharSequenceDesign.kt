package funcify.naming.charseq.design

import arrow.core.Either
import funcify.naming.charseq.operator.MapCharSeqOperator
import funcify.naming.charseq.operator.StartEndStringGroupingCharSeqOperator
import funcify.naming.charseq.template.CharSequenceTransformationTemplate


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceDesign<CS, CSI> {

    val charSeq: Either<CSI, CS>

    fun map(mapper: (Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { _, c -> mapper.invoke(c) }
    }

    fun mapWithIndex(mapper: (Int, Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { i, c ->
            mapper.invoke(i,
                          c)
        }
    }

    fun groupByDelimiter(delimiter: Char): CharSequenceIterableDesign<CS, CSI> {
        return StartEndStringGroupingCharSeqOperator<CS, CSI>(charSeq,
                                                              delimiter.toString(),
                                                              delimiter.toString())
    }

    fun groupByStartAndEnd(startStr: String,
                           endStr: String): CharSequenceIterableDesign<CS, CSI> {
        return StartEndStringGroupingCharSeqOperator<CS, CSI>(charSeq,
                                                              startStr,
                                                              endStr)
    }

    fun <CST> fold(template: CST): CS where CST : CharSequenceTransformationTemplate<CS, CSI>

}