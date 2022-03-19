package funcify.naming.charseq.template

import funcify.naming.charseq.operation.CharSequenceFilterOperation
import funcify.naming.charseq.operation.CharSequenceMapOperation
import funcify.naming.charseq.operation.CharacterFilterOperation
import funcify.naming.charseq.operation.CharacterGroupFlatteningOperation
import funcify.naming.charseq.operation.CharacterGroupingOperation
import funcify.naming.charseq.operation.CharacterMapOperation


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceTransformationTemplate<CS, CSI> {

    //    fun emptyCharSeq(): CS
    //
    //    fun headCharSeqFromIterableOrEmpty(charSeqIterable: CSI): CS
    //
    //    fun singletonCharSeqIterable(charSeq: CS): CSI

    fun filterCharacters(filter: (Char) -> Boolean): CharacterFilterOperation<CS>

    fun mapCharacters(mapper: (Char) -> Char): CharacterMapOperation<CS>

    fun mapCharactersWithIndex(mapper: (Int, Char) -> Char): CharacterMapOperation<CS>

    fun groupCharactersByDelimiter(delimiter: Char): CharacterGroupingOperation<CS, CSI>

    fun mapCharacterSequence(mapper: (CSI) -> CSI): CharSequenceMapOperation<CSI>

    fun filterCharacterSequence(mapper: (CSI) -> CSI): CharSequenceFilterOperation<CSI>

    fun flattenCharacterSequence(mapper: (CSI) -> CS): CharacterGroupFlatteningOperation<CS, CSI>

}