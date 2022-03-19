package funcify.naming.charseq.template


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceTransformationTemplate<CS, CSI> {

    fun emptyCharSeq(): CS

    fun headCharSeqFromIterableOrEmpty(charSeqIterable: CSI): CS

    fun singletonCharSeqIterable(charSeq: CS): CSI

    fun map(charSeq: CS,
            mapper: (Char) -> Char): CS

    fun mapWithIndex(charSeq: CS,
                     mapper: (Int, Char) -> Char): CS

    fun groupByDelimiter(charSeq: CS,
                         delimiter: Char): CSI

    fun groupByStartAndEnd(charSeq: CS,
                           startStr: String,
                           endStr: String): CSI

    fun flatMapCharSeqIterable(charSeqIterable: CSI,
                               mapper: (CSI) -> CS): CS
}