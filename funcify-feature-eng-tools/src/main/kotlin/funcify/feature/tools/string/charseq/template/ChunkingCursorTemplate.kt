package funcify.feature.tools.string.charseq.template


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface ChunkingCursorTemplate<I, O> : CharacterSequenceCursorTemplate<I, O> {

    override fun onAnyCharacter(inputContext: I,
                                outputContext: O): O {
        return if (hasCurrentInputChar(inputContext) && currentCharacterInDelimiterSet(inputContext)) {
            onDelimiterCharacter(inputContext,
                                 outputContext)
        } else {
            outputContext
        }
    }

    fun currentCharacterInDelimiterSet(inputContext: I): Boolean

    fun onDelimiterCharacter(inputContext: I,
                             outputContext: O): O

}