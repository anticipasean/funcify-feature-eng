package funcify.feature.tools.string.charseq.factory

import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
interface StringBuilderCursorOutputFactory<I> : CharacterSequenceCursorTemplate<I, StringBuilder> {

    override fun appendToTailOfOutput(character: Char,
                                      inputContext: I,
                                      outputContext: StringBuilder): StringBuilder {
        return outputContext.append(character)
    }

//    override fun removeFromTailOfOutput(character: Char,
//                                        outputContext: StringBuilder): StringBuilder {
//        return if (outputContext.isNotEmpty() && outputContext[outputContext.length - 1] == character) {
//            outputContext.deleteCharAt(outputContext.length - 1)
//        } else {
//            outputContext
//        }
//    }
}