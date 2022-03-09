package funcify.feature.tools.string.charseq.impl

import funcify.feature.tools.string.charseq.strategy.CharacterHandlingStrategy
import funcify.feature.tools.string.charseq.template.CharacterSequenceCursorTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
class SnakeCaseFactory(val asciiCharacterContext: AsciiCharacterContext,
                       val characterHandlingStrategy: CharacterHandlingStrategy<AsciiCharacterContext>,
                       val characterSequenceContext: CharacterSequenceContext,
                       val characterSequenceCursorTemplate: CharacterSequenceCursorTemplate<AsciiCharacterContext, CharacterSequenceContext>) {

    fun transformIntoSnakeCase(input: String) : String {



    }

}