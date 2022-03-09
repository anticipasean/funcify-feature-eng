package funcify.feature.tools.string.charseq.impl


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
data class CharacterSequenceContext(val inputString: String,
                                    private val inputStringLength: Int = inputString.length,
                                    private var cursorIndex: Int = 0,
                                    private val outputStringBuilder: StringBuilder = StringBuilder()) {

    fun hasPreviousChar(): Boolean {
        return cursorIndex > 0 && inputStringLength > 0
    }

    fun hasNextChar(): Boolean {
        return cursorIndex >= 0 && inputStringLength > 0 && cursorIndex < (inputStringLength - 1)
    }

    fun moveToPreviousChar(): Char? {
        return if (hasPreviousChar()) {
            inputString[cursorIndex--]
        } else {
            null
        }
    }

    fun moveToNextChar(): Char? {
        return if (hasNextChar()) {
            inputString[cursorIndex++]
        } else {
            null
        }
    }

    fun addCharToOutput(character: Char) {
        outputStringBuilder.append(character)
    }

}

