package funcify.naming.charseq

import java.util.Spliterators
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class CharArraySourceSequence(private val charArray: CharArray,
                              private val additionalCharacteristics: Int = SIZED and NONNULL and IMMUTABLE and ORDERED) : Spliterators.AbstractSpliterator<CharContext>(charArray.size.toLong(),
                                                                                                                                                                        additionalCharacteristics),
                                                                                                                          ContextualCharSequence {

    private val size: Int = charArray.size
    private var index: Int = -1

    override fun tryAdvance(action: Consumer<in CharContext>?): Boolean {
        if (action == null || size <= 0 || index + 1 >= size - 1) {
            return false
        }
        action.accept(when (++index) {
                          0 -> {
                              CharContext(charArray[index],
                                          index,
                                          RelativeCharSequencePosition.FIRST_CHARACTER)
                          }
                          (size - 1) -> {
                              CharContext(charArray[index],
                                          index,
                                          RelativeCharSequencePosition.LAST_CHARACTER)
                          }
                          else -> {
                              CharContext(charArray[index],
                                          index,
                                          RelativeCharSequencePosition.MIDDLE_CHARACTER)
                          }
                      })
        return true
    }

}