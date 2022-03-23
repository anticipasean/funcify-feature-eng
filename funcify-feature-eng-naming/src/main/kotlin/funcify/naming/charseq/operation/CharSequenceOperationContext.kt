package funcify.naming.charseq.operation

import kotlinx.collections.immutable.ImmutableList


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceOperationContext<in I, CS, CSI> {

    val inputToStringTransformer: (I) -> CS
    val allCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val allCharacterMapOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val leadingCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val leadingCharacterMapOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val trailingCharacterFilterOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val trailingCharacterMapOperations: ImmutableList<CharacterMapOperation<CS, CSI>>
    val segmentingOperations: ImmutableList<CharacterGroupingOperation<CS, CSI>>
    val segmentFilterOperations: ImmutableList<CharSequenceMapOperation<CS, CSI>>
    val segmentLeadingFilterOperations: ImmutableList<CharSequenceMapOperation<CS, CSI>>
    val segmentTrailingFilterOperations: ImmutableList<CharSequenceMapOperation<CS, CSI>>
    val segmentMapOperations: ImmutableList<CharSequenceMapOperation<CS, CSI>>


}