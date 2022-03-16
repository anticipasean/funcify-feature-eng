package funcify.naming.charseq.context


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
data class IndexedChar(val character: Char,
                       val index: Int) : Comparable<IndexedChar> {

    companion object {
        internal val DEFAULT_COMPARATOR: Comparator<in IndexedChar> by lazy { Comparator.comparing(IndexedChar::index) }
    }

    override fun compareTo(other: IndexedChar): Int {
        return DEFAULT_COMPARATOR.compare(this,
                                          other)
    }

}

internal infix fun Char.at(index: Int): IndexedChar {
    return IndexedChar(this,
                       index)
}

internal infix fun CharArray.at(index: Int): IndexedChar {
    return IndexedChar(this[index],
                       index)
}

