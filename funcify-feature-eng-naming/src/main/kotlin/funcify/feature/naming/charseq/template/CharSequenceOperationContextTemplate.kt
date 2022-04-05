package funcify.feature.naming.charseq.template

import kotlinx.collections.immutable.ImmutableList


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceOperationContextTemplate<CTX> {

    fun emptyContext(): CTX

    fun filterCharacters(context: CTX,
                         filter: (Char) -> Boolean): CTX

    fun mapCharacters(context: CTX,
                      mapper: (Char) -> CharSequence): CTX

    fun mapCharactersWithIndex(context: CTX,
                               mapper: (Int, Char) -> CharSequence): CTX

    fun mapCharactersWithWindow(context: CTX,
                                windowSize: UInt,
                                windowMapper: (ImmutableList<Char>) -> Char): CTX

    fun mapCharactersWithPairWindow(context: CTX,
                                    windowMapper: (Pair<Char?, Char?>) -> Char): CTX

    fun mapCharactersWithTripleWindow(context: CTX,
                                      windowMapper: (Triple<Char?, Char, Char?>) -> CharSequence): CTX

    fun groupCharactersByDelimiter(context: CTX,
                                   delimiter: Char): CTX

    fun filterCharacterSequence(context: CTX,
                                filter: (CharSequence) -> Boolean): CTX

    fun splitCharacterSequences(context: CTX,
                                mapper: (CharSequence) -> Iterable<CharSequence>): CTX

    fun mapCharacterSequence(context: CTX,
                             mapper: (CharSequence) -> CharSequence): CTX

    fun mapCharacterSequenceWithWindow(context: CTX,
                                       windowSize: UInt,
                                       mapper: (ImmutableList<CharSequence>) -> Iterable<CharSequence>): CTX

    fun mapCharacterSequenceWithPairWindow(context: CTX,
                                           mapper: (Pair<CharSequence?, CharSequence?>) -> Iterable<CharSequence>): CTX

    fun mapCharacterSequenceWithTripleWindow(context: CTX,
                                             mapper: (Triple<CharSequence?, CharSequence, CharSequence?>) -> Iterable<CharSequence>): CTX

    fun mapCharacterSequenceWithIndex(context: CTX,
                                      mapper: (Int, CharSequence) -> CharSequence): CTX

    fun prependCharacterSequence(context: CTX,
                                 charSequence: CharSequence): CTX

    fun appendCharacterSequence(context: CTX,
                                charSequence: CharSequence): CTX

}