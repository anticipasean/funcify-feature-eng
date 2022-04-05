package funcify.feature.naming.charseq.group

import funcify.feature.naming.charseq.spliterator.DuplicatingSpliterator
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
internal abstract class BaseCharGroup(private val inputSpliterator: Spliterator<Char>) : CharGroup {

    private val stringFormSpliterator: DuplicatingSpliterator<Char> = DuplicatingSpliterator(inputSpliterator)

    override val groupSpliterator: Spliterator<Char> = stringFormSpliterator.duplicate()

    private val stringForm: String by lazy {
        val sb: StringBuilder = StringBuilder()
        stringFormSpliterator.forEachRemaining { c ->
            sb.append(c)
        }
        sb.toString()
    }

    override val length: Int by lazy { stringForm.length }

    override fun get(index: Int): Char {
        return stringForm[index]
    }

    override fun isEmpty(): Boolean {
        return stringForm.isEmpty()
    }

    override fun subSequence(startIndex: Int,
                             endIndex: Int): CharSequence {
        return stringForm.subSequence(startIndex,
                                      endIndex)
    }

    override fun toString(): String {
        return stringForm
    }

}
