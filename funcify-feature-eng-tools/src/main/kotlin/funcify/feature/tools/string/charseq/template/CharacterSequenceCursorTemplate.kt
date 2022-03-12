package funcify.feature.tools.string.charseq.template

import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface CharacterSequenceCursorTemplate<I, O> {

    fun currentRelativeLocation(inputContext: I): RelativeCharSequenceLocation?

    fun hasCurrentInputChar(inputContext: I): Boolean

    fun currentInputChar(inputContext: I): Char

    fun hasPreviousCharInput(inputContext: I): Boolean

    fun moveToPreviousCharInput(inputContext: I): I

    fun hasNextInputChar(inputContext: I): Boolean

    fun moveToNextInputChar(inputContext: I): I

    fun appendToTailOfOutput(character: Char,
                             inputContext: I,
                             outputContext: O): O

    fun onAnyCharacter(inputContext: I,
                       outputContext: O): O
}