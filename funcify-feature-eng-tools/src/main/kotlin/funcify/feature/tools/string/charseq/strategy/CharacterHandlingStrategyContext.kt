package funcify.feature.tools.string.charseq.strategy

import kotlinx.collections.immutable.ImmutableList


/**
 *
 * @author smccarron
 * @created 3/8/22
 */
interface CharacterHandlingStrategyContext<I> {

    val anyCharacterStrategies: ImmutableList<CharacterHandlingStrategy<I>>

    val firstCharacterStrategies: ImmutableList<FirstCharacterHandlingStrategy<I>>

    val middleCharacterStrategies: ImmutableList<MiddleCharacterHandlingStrategy<I>>

    val lastCharacterStrategies: ImmutableList<LastCharacterHandlingStrategy<I>>

}