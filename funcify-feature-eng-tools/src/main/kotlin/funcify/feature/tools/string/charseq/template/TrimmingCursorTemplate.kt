package funcify.feature.tools.string.charseq.template


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface TrimmingCursorTemplate<I, O> : CharacterPositionStrategyTemplate<I, O> {

    override fun onFirstCharacter(inputContext: I,
                                  outputContext: O): O {
        while (currentInputChar(inputContext).isWhitespace() && hasNextInputChar(inputContext)) {
            moveToNextInputChar(inputContext)
        }
        return outputContext
    }

    override fun onLastCharacter(inputContext: I,
                                 outputContext: O): O {
        var updatedOutput = outputContext
//        while (currentInputChar(inputContext).isWhitespace() && hasPreviousCharInput(inputContext)) {
//            updatedOutput = removeFromTailOfOutput(currentInputChar(inputContext),
//                                                   updatedOutput)
//            moveToPreviousCharInput(inputContext)
//        }
        return updatedOutput
    }

}