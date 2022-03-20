package funcify.naming.charseq.template


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceContextTransformationTemplate<CTX, CS, CSI> {

    fun filterCharacters(context: CTX,
                         filter: (Char) -> Boolean): CTX

    fun filterCharactersWithIndex(context: CTX,
                                  filter: (Int, Char) -> Boolean): CTX

    fun mapCharacters(context: CTX,
                      mapper: (Char) -> Char): CTX

    fun mapCharactersWithIndex(context: CTX,
                               mapper: (Int, Char) -> Char): CTX

    fun groupCharactersByDelimiter(context: CTX,
                                   delimiter: Char): CTX

    fun mapCharacterSequence(context: CTX,
                             mapper: (CSI) -> CSI): CTX

}