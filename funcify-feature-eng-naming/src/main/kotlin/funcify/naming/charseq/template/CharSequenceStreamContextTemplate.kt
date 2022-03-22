package funcify.naming.charseq.template

import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.operation.DefaultCharSequenceOperationFactory


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceStreamContextTemplate<I> : CharSequenceOperationContextTemplate<CharSequenceStreamContext<I>> {

    override fun filterLeadingCharacters(context: CharSequenceStreamContext<I>,
                                         filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        val leadingCharacterFilterOperations =
                context.leadingCharacterFilterOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation({ cs -> cs.filter(filter) }))
        return context.copy(leadingCharacterFilterOperations = leadingCharacterFilterOperations)
    }

    override fun filterTrailingCharacters(context: CharSequenceStreamContext<I>,
                                          filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun filterAnyCharacters(context: CharSequenceStreamContext<I>,
                                     filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun mapCharacters(context: CharSequenceStreamContext<I>,
                               mapper: (Char) -> Char): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun mapCharactersWithIndex(context: CharSequenceStreamContext<I>,
                                        mapper: (Int, Char) -> Char): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun groupCharactersByDelimiter(context: CharSequenceStreamContext<I>,
                                            delimiter: Char): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun filterLeadingCharacterSequence(context: CharSequenceStreamContext<I>,
                                                filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun filterTrailingCharacterSequence(context: CharSequenceStreamContext<I>,
                                                 filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun filterCharacterSequence(context: CharSequenceStreamContext<I>,
                                         filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun mapCharacterSequence(context: CharSequenceStreamContext<I>,
                                      mapper: (CharSequence) -> CharSequence): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }

    override fun mapCharacterSequenceWithIndex(context: CharSequenceStreamContext<I>,
                                               mapper: (Int, CharSequence) -> CharSequence): CharSequenceStreamContext<I> {
        TODO("Not yet implemented")
    }
}