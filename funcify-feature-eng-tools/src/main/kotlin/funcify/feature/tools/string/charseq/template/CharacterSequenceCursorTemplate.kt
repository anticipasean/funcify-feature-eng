package funcify.feature.tools.string.charseq.template

import funcify.feature.tools.string.charseq.RelativeCharSequenceLocation


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface CharacterSequenceCursorTemplate<I, O> {

    fun currentRelativeLocation(inputContext: I): RelativeCharSequenceLocation

    fun hasPrevious(inputContext: I): Boolean

    fun moveToPrevious(inputContext: I): I

    fun hasNext(inputContext: I): Boolean

    fun moveToNext(inputContext: I): I

    fun onFirstCharacter(inputContext: I,
                         outputContext: O): O

    fun onMiddleCharacter(inputContext: I,
                          outputContext: O): O

    fun onLastCharacter(inputContext: I,
                        outputContext: O): O

    fun onUnderscore(inputContext: I,
                     outputContext: O): O

    fun onAlphabeticLetter(inputContext: I,
                           outputContext: O): O

    fun onUppercaseLetter(inputContext: I,
                          outputContext: O): O

    fun onLowercaseLetter(inputContext: I,
                          outputContext: O): O

    fun onNumeric(inputContext: I,
                  outputContext: O): O

    fun onWhitespace(inputContext: I,
                     outputContext: O): O

    fun onUnhandled(inputContext: I,
                    outputContext: O): O
}