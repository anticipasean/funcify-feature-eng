package funcify.feature.naming.charseq.context

import funcify.feature.naming.charseq.operation.CharSequenceMapOperation
import funcify.feature.naming.charseq.operation.CharacterGroupingOperation
import funcify.feature.naming.charseq.operation.CharacterMapOperation
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal data class CharSequenceStreamOpContext<I>(override val inputToCharSequenceTransformer: (I) -> Stream<CharSequence>,
                                                   val characterMapOperations: PersistentList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                   val segmentingOperations: PersistentList<CharacterGroupingOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf(),
                                                   val segmentMapOperations: PersistentList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>> = persistentListOf()) : CharSequenceOperationContext<I, Stream<Char>, Stream<CharSequence>> {

}
