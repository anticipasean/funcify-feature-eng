package funcify.naming.charseq.operation

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
data class OperationContext<in I, CS, CSI>(val inputToStringTransformer: (I) -> CS,
                                           val allCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>> = persistentListOf(),
                                           val allCharacterMapOperations: PersistentList<CharacterMapOperation<CS>> = persistentListOf(),
                                           val leadingCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>> = persistentListOf(),
                                           val leadingCharacterMapOperations: PersistentList<CharacterMapOperation<CS>> = persistentListOf(),
                                           val trailingCharacterFilterOperations: PersistentList<CharacterFilterOperation<CS>> = persistentListOf(),
                                           val trailingCharacterMapOperations: PersistentList<CharacterMapOperation<CS>> = persistentListOf(),
                                           val segmentFilterOperations: PersistentList<CharSequenceFilterOperation<CSI>> = persistentListOf(),
                                           val segmentOperations: PersistentList<CharSequenceMapOperation<CSI>> = persistentListOf()) {


}
