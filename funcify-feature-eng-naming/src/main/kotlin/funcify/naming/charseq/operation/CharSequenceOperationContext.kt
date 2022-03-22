package funcify.naming.charseq.operation

import kotlinx.collections.immutable.ImmutableList


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceOperationContext<in I, CS, CSI> {

    val inputToStringTransformer: (I) -> CS
    val allCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS>>
    val allCharacterMapOperations: ImmutableList<CharacterMapOperation<CS>>
    val leadingCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS>>
    val leadingCharacterMapOperations: ImmutableList<CharacterMapOperation<CS>>
    val trailingCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS>>
    val trailingCharacterMapOperations: ImmutableList<CharacterMapOperation<CS>>
    val segmentFilterOperations: ImmutableList<CharSequenceMapOperation<CSI>>
    val segmentOperations: ImmutableList<CharSequenceMapOperation<CSI>>


}