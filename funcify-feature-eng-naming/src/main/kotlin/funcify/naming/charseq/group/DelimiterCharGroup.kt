package funcify.naming.charseq.group

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.spliterator.DuplicatingSpliterator
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
data class DelimiterCharGroup(private val inputSpliterator: Spliterator<IndexedChar>) : DelimiterBasedGrouping {
    private val stringFormSpliterator: DuplicatingSpliterator<IndexedChar> = DuplicatingSpliterator(inputSpliterator)
    override val groupSpliterator: Spliterator<IndexedChar> = stringFormSpliterator.duplicate()

    private val stringForm: String by lazy {
        val sb: StringBuilder = StringBuilder()
        stringFormSpliterator.forEachRemaining { ic ->
            sb.append(ic.character)
        }
        sb.toString()
    }

    override fun toString(): String {
        return stringForm
    }
}
