package funcify.naming.charseq.operation

import kotlinx.collections.immutable.PersistentList


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
data class OperationContext<CS, CSI>(val allCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>>,
                                     val allCharacterMapOperations: PersistentList<CharacterMapOperation<CS>>,
                                     val leadingCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>>,
                                     val leadingCharacterMapOperations: PersistentList<CharacterMapOperation<CS>>,
                                     val trailingCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>>,
                                     val trailingCharacterMapOperations: PersistentList<CharacterMapOperation<CS>>,
                                     val segmentFilterOperations: PersistentList<CharSequenceFilterOperation<CSI>>,
                                     val segmentOperations: PersistentList<CharSequenceMapOperation<CSI>>) {


}
