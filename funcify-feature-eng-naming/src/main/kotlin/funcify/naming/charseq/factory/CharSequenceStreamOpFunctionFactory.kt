package funcify.naming.charseq.factory

import arrow.core.andThen
import funcify.naming.charseq.context.CharSequenceStreamContext
import funcify.naming.charseq.extension.CharSequenceExtensions.stream
import funcify.naming.charseq.operation.CharSequenceMapOperation
import funcify.naming.charseq.operation.CharacterMapOperation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream

internal class CharSequenceStreamOpFunctionFactory<I : Any> : (CharSequenceStreamContext<I>) -> (I) -> ImmutableList<String> {

    companion object {

        private val DEFAULT_INSTANCE: CharSequenceStreamOpFunctionFactory<Any> by lazy {
            CharSequenceStreamOpFunctionFactory<Any>()
        }

        fun <I : Any> getInstance(): CharSequenceStreamOpFunctionFactory<I> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as CharSequenceStreamOpFunctionFactory<I>
        }
    }

    override fun invoke(streamOpContext: CharSequenceStreamContext<I>): (I) -> ImmutableList<String> {
        val characterMapperFunction: (Stream<Char>) -> Stream<Char> = composeCharacterMappingFunction(streamOpContext)
        val characterSequenceMapperFunction: (Stream<CharSequence>) -> Stream<CharSequence> = composeCharacterSequenceMappingFunction(streamOpContext)
        return { input: I ->
            characterSequenceMapperFunction.invoke(streamOpContext.inputToCharSequenceTransformer.invoke(input)
                                                           .map { cs ->
                                                               characterMapperFunction.invoke(cs.stream())
                                                                       .reduce(StringBuilder(),
                                                                               { sb, c -> sb.append(c) },
                                                                               { sb1, sb2 -> sb1.append(sb2) })
                                                           })
                    .reduce(persistentListOf(),
                            { pl, cs -> pl.add(cs.toString()) },
                            { pl1, pl2 -> pl1.addAll(pl2) })
        }
    }

    private fun composeCharacterMappingFunction(streamOpContext: CharSequenceStreamContext<I>): (Stream<Char>) -> Stream<Char> {
        return sequenceOf(streamOpContext.allCharacterFilterOperations,
                          streamOpContext.allCharacterMapOperations,
                          streamOpContext.leadingCharacterFilterOperations,
                          streamOpContext.leadingCharacterMapOperations,
                          streamOpContext.trailingCharacterFilterOperations,
                          streamOpContext.trailingCharacterMapOperations).fold({ cStream: Stream<Char> -> cStream },
                                                                               ::characterMappingFunctionFold)
    }

    private fun characterMappingFunctionFold(function: (Stream<Char>) -> Stream<Char>,
                                             opList: ImmutableList<CharacterMapOperation<Stream<Char>, Stream<CharSequence>>>): (Stream<Char>) -> Stream<Char> {
        return opList.fold(function) { func: (Stream<Char>) -> Stream<Char>, op: CharacterMapOperation<Stream<Char>, Stream<CharSequence>> ->
            func.andThen(op)
        }
    }

    private fun composeCharacterSequenceMappingFunction(streamOpContext: CharSequenceStreamContext<I>): (Stream<CharSequence>) -> Stream<CharSequence> {
        return sequenceOf(streamOpContext.segmentLeadingFilterOperations,
                          streamOpContext.segmentFilterOperations,
                          streamOpContext.segmentMapOperations,
                          streamOpContext.segmentTrailingFilterOperations).fold({ csStream: Stream<CharSequence> -> csStream },
                                                                                ::characterSequenceMappingFunctionFold)
    }

    private fun characterSequenceMappingFunctionFold(function: (Stream<CharSequence>) -> Stream<CharSequence>,
                                                     opList: ImmutableList<CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>>>): (Stream<CharSequence>) -> Stream<CharSequence> {
        return opList.fold(function) { func: (Stream<CharSequence>) -> Stream<CharSequence>, op: CharSequenceMapOperation<Stream<Char>, Stream<CharSequence>> ->
            func.andThen(op)
        }
    }
}