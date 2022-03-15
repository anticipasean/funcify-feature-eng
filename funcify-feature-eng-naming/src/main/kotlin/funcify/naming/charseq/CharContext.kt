package funcify.naming.charseq


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
data class CharContext(val character: Char,
                       val index: Int,
                       val relationPosition: RelativeCharSequencePosition) : Comparable<CharContext> {

    companion object {
        internal val DEFAULT_COMPARATOR: Comparator<in CharContext> by lazy { Comparator.comparing(CharContext::index) }
    }

    override fun compareTo(other: CharContext): Int {
        return DEFAULT_COMPARATOR.compare(this,
                                          other)
    }

}
