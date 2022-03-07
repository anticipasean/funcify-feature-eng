package funcify.feature.tools.string


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
interface CharacterHandlerTemplate<CH, SBC> {

    fun onNext(character: Char,
               characterHandler: CH,
               stringBuilderContext: SBC): SBC {
        return when (character) {
            '_' -> onUnderscore(character,
                                characterHandler,
                                stringBuilderContext)

            else -> {
                onUnhandled(character,
                            characterHandler,
                            stringBuilderContext)
            }
        }
    }

    fun addCharacter(character: Char,
                     stringBuilderContext: SBC): SBC

    fun dropCharacter(character: Char,
                      stringBuilderContext: SBC): SBC

    fun onUnderscore(character: Char,
                     characterHandler: CH,
                     stringBuilderContext: SBC): SBC

    fun onUppercaseLetter(character: Char,
                          characterHandler: CH,
                          stringBuilderContext: SBC): SBC

    fun onLowercaseLetter(character: Char,
                          characterHandler: CH,
                          stringBuilderContext: SBC): SBC

    fun onNumeric(character: Char,
                  characterHandler: CH,
                  stringBuilderContext: SBC): SBC

    fun onWhitespace(character: Char,
                     characterHandler: CH,
                     stringBuilderContext: SBC): SBC

    fun onUnhandled(character: Char,
                    characterHandler: CH,
                    stringBuilderContext: SBC): SBC
}