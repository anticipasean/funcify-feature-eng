package funcify.naming.charseq.operation

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal data class CharSequenceStreamContext<I>(override val inputToCharSequenceTransformer: (I) -> Stream<CharSequence>,
                                                 override val allCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val allCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val leadingCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val leadingCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val trailingCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val trailingCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val segmentingOperations: PersistentList<CharacterGroupingOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val segmentLeadingFilterOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val segmentFilterOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val segmentTrailingFilterOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                 override val segmentMapOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf()) : CharSequenceOperationContext<I, Stream<Char>, Stream<CharSequence>> {

}
