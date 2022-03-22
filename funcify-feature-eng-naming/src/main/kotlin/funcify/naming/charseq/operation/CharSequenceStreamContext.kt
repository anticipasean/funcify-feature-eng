package funcify.naming.charseq.operation

import kotlinx.collections.immutable.PersistentList
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
data class CharSequenceStreamContext<in I>(override val inputToStringTransformer: (I) -> Stream<Char>,
                                           override val allCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val allCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val leadingCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val leadingCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val trailingCharacterFilterOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val trailingCharacterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val segmentFilterOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>>,
                                           override val segmentOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>>) : CharSequenceOperationContext<I, Stream<Char>, Stream<CharSequence>> {

}
