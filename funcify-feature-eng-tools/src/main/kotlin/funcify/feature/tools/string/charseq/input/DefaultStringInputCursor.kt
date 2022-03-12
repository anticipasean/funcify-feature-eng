package funcify.feature.tools.string.charseq.input


/**
 *
 * @author smccarron
 * @created 3/9/22
 */
data class DefaultStringInputCursor(val inputString: String,
                                    private val inputStringLength: Int = inputString.length,
                                    private var currentIndex: Int = -1) {

    fun hasPrevious(): Boolean {
        return inputStringLength > 0 && currentIndex > 0
    }

    fun hasNext(): Boolean {
        return inputStringLength > 0 && currentIndex < (inputStringLength - 1)
    }

    fun moveToPrevious() {
        if (hasPrevious()) {
            currentIndex--
        }
    }

    fun moveToNext() {
        if (hasNext()) {
            currentIndex++
        }
    }

    fun hasCurrent(): Boolean {
        return inputStringLength > 0 && currentIndex >= 0 && currentIndex < inputStringLength
    }

    fun getCurrentChar(): Char {
        return if (hasCurrent()) {
            inputString[currentIndex]
        } else {
            throw NoSuchElementException("no char is present at [ current_index: $currentIndex ]")
        }
    }


}
