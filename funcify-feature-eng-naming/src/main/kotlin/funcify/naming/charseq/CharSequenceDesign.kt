package funcify.naming.charseq


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceDesign<CS, CSI> {

    val charSeq: Either<CSI, CS>

    fun map(mapper: (Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { _, _, c -> mapper.invoke(c) }
    }

    fun mapWithIndex(mapper: (Int, Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { i, _, c ->
            mapper.invoke(i,
                          c)
        }
    }

    fun mapWithRelativePosition(mapper: (RelativeCharSequencePosition, Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { _, rp, c ->
            mapper.invoke(rp,
                          c)
        }
    }

    fun mapWithIndexAndRelativePosition(mapper: (Int, RelativeCharSequencePosition, Char) -> Char): CharSequenceDesign<CS, CSI> {
        return MapCharSeqOperator<CS, CSI>(charSeq) { i, rp, c ->
            mapper.invoke(i,
                          rp,
                          c)
        }
    }

    fun mapWithCharArrayWindow(windowSize: UInt,
                               mapper: (Array<Char>) -> Char): CharSequenceDesign<CS, CSI> {
        return CharArrayWindowCharSeqOperator<CS, CSI>(charSeq,
                                                       windowSize,
                                                       mapper)
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

    fun <CST> fold(template: CST): CS where CST : CharSequenceTemplate<CS, CSI>

}