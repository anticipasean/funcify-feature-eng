package funcify.naming.charseq.context


/**
 *
 * @author smccarron
 * @created 3/18/22
 */
object IndexedCharExtensions {

    internal infix fun Char.at(index: Int): IndexedChar {
        return IndexedChar(this,
                           index)
    }

    internal infix fun CharArray.at(index: Int): IndexedChar {
        return IndexedChar(this[index],
                           index)
    }

}