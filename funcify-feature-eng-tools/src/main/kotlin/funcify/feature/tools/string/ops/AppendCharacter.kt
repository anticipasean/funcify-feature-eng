package funcify.feature.tools.string.ops

import funcify.feature.tools.string.CharacterHandlerDesign
import funcify.feature.tools.string.CharacterHandlerTemplate


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
data class AppendCharacter<CH>(val characterHandler: CH,
                               val character: Char) : CharacterHandlerDesign<CH> {

    override fun <SBC> fold(characterHandlerTemplate: CharacterHandlerTemplate<CH, SBC>,
                            stringBuilderContext: SBC): SBC {
        return characterHandlerTemplate.addCharacter(character,
                                                     stringBuilderContext)
    }

}
