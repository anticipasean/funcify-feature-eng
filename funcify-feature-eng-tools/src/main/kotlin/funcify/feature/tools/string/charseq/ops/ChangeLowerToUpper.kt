package funcify.feature.tools.string.charseq.ops

import funcify.feature.tools.string.charseq.strategy.CharacterHandlingStrategy
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
class ChangeLowerToUpper<I>(val character: Char) : CharacterHandlingStrategy<I> {

    override fun <O> fold(cursorTemplate: CharacterSequenceCursorTemplate<I, O>,
                          inputContext: I,
                          outputContext: O): O {
        TODO("not yet implemented")
    }

}
