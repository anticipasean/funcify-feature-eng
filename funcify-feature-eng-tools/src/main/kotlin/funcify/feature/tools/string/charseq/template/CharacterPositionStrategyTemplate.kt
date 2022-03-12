package funcify.feature.tools.string.charseq.template

import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.FIRST_CHARACTER
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.LAST_CHARACTER
import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation.MIDDLE_CHARACTER


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface CharacterPositionStrategyTemplate<I, O> : CharacterSequenceCursorTemplate<I, O> {

    override fun onAnyCharacter(inputContext: I,
                                outputContext: O): O {
        return if (hasCurrentInputChar(inputContext)) {
            when (currentRelativeLocation(inputContext)) {
                FIRST_CHARACTER -> onFirstCharacter(inputContext,
                                                    outputContext)
                MIDDLE_CHARACTER -> onMiddleCharacter(inputContext,
                                                      outputContext)
                LAST_CHARACTER -> onLastCharacter(inputContext,
                                                  outputContext)
                null -> outputContext
            }
        } else {
            outputContext
        }
    }

    fun onFirstCharacter(inputContext: I,
                         outputContext: O): O

    fun onMiddleCharacter(inputContext: I,
                          outputContext: O): O

    fun onLastCharacter(inputContext: I,
                        outputContext: O): O

}