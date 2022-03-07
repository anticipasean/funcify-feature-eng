package funcify.feature.tools.string

import funcify.feature.tools.string.ops.AppendCharacter
import funcify.feature.tools.string.ops.ChangeLowerToUpper
import funcify.feature.tools.string.ops.ChangeUpperToLower
import funcify.feature.tools.string.ops.DropCharacter


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface CharacterHandlerDesign<CH> {

    fun appendUnderscore(characterHandler: CH): CharacterHandlerDesign<CH> {
        return AppendCharacter<CH>(characterHandler, '_')
    }

    fun appendWhitespace(characterHandler: CH): CharacterHandlerDesign<CH> {
        return AppendCharacter<CH>(characterHandler, ' ')
    }

    fun dropWhitespace(characterHandler: CH): CharacterHandlerDesign<CH> {
        return DropCharacter<CH>(characterHandler, ' ')
    }

    fun changeLowercaseToUppercase(characterHandler: CH, character: Char): CharacterHandlerDesign<CH> {
        return ChangeLowerToUpper<CH>(characterHandler, character)
    }

    fun changeUppercaseToLowercase(characterHandler: CH, character: Char): CharacterHandlerDesign<CH> {
        return ChangeUpperToLower<CH>(characterHandler, character)
    }

    fun <SBC> fold(characterHandlerTemplate: CharacterHandlerTemplate<CH, SBC>,
                   stringBuilderContext: SBC): SBC

}