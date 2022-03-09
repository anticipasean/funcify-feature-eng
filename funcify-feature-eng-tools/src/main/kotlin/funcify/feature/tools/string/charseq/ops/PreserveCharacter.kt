package funcify.feature.tools.string.charseq.ops

import funcify.feature.tools.string.charseq.strategy.MiddleCharacterHandlingStrategy
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
class PreserveCharacter<I>() : MiddleCharacterHandlingStrategy<I> {

    override fun <O> fold(cursorTemplate: CharacterSequenceCursorTemplate<I, O>,
                          inputContext: I,
                          outputContext: O): O {
        TODO("not yet implemented")
    }

}
