package funcify.feature.tools.string.charseq.impl

import funcify.feature.tools.string.charseq.strategy.CharacterHandlingStrategy
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
data class DefaultCharacterHandlingStrategy<CHC>(override val inputContext: CHC) : CharacterHandlingStrategy<CHC> {

    override fun <SBC> fold(cursorTemplate: CharacterSequenceCursorTemplate<CHC, SBC>,
                            inputContext: CHC,
                            outputContext: SBC): SBC {
        // This is the start so don't do anything
        return outputContext
    }

}