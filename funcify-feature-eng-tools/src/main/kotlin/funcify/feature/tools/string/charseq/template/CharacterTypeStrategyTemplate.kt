package funcify.feature.tools.string.charseq.template


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface CharacterTypeStrategyTemplate<I, O> : CharacterSequenceCursorTemplate<I, O> {

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