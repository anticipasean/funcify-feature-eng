package funcify.feature.tools.string.charseq.ops

import funcify.feature.tools.string.charseq.strategy.LastCharacterHandlingStrategy
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
class DropNonAlphanumericLastCharacters<I>() : LastCharacterHandlingStrategy<I> {


    override fun <O> fold(cursorTemplate: CharacterSequenceCursorTemplate<I, O>,
                          inputContext: I,
                          outputContext: O): O {
        TODO("Not yet implemented")
    }

}
