package funcify.naming.charseq


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceTemplate<CS, CSI> {

    fun emptyCharSeq(): CS

    fun headCharSeqFromIterableOrEmpty(charSeqIterable: CSI): CS

    fun singletonCharSeqIterable(charSeq: CS): CSI

    fun map(charSeq: CS,
            mapper: (Char) -> Char): CS

    fun mapWithIndex(charSeq: CS,
                     mapper: (Int, Char) -> Char): CS

    fun mapWithRelativePosition(charSeq: CS,
                                mapper: (RelativeCharSequencePosition, Char) -> Char): CS

    fun mapWithIndexAndRelativePosition(charSeq: CS,
                                        mapper: (Int, RelativeCharSequencePosition, Char) -> Char): CS

    fun mapWithCharArrayWindow(charSeq: CS,
                               windowSize: UInt,
                               mapper: (Array<Char>) -> Char): CS

    fun groupByDelimiter(charSeq: CS,
                         delimiter: Char): CSI

    fun groupByStartAndEnd(charSeq: CS,
                           startStr: String,
                           endStr: String): CSI

    fun flatMapCharSeqIterable(charSeqIterable: CSI,
                               mapper: (CSI) -> CS): CS
}