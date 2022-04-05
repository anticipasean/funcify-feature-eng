package funcify.feature.naming.charseq.factory


import funcify.feature.naming.charseq.context.CharSequenceStreamOpContext
import funcify.feature.naming.charseq.extension.CharSequenceExtensions.stream
import funcify.feature.naming.function.FunctionExtensions.andThen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream

internal class CharSequenceStreamOpFunctionFactory<I : Any> : (CharSequenceStreamOpContext<I>) -> (I) -> ImmutableList<String> {

    companion object {

        private val DEFAULT_INSTANCE: CharSequenceStreamOpFunctionFactory<Any> by lazy {
            CharSequenceStreamOpFunctionFactory<Any>()
        }

        fun <I : Any> getInstance(): CharSequenceStreamOpFunctionFactory<I> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as CharSequenceStreamOpFunctionFactory<I>
        }
    }

    override fun invoke(streamOpContext: CharSequenceStreamOpContext<I>): (I) -> ImmutableList<String> {
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

    private fun composeCharacterMappingFunction(streamOpContext: CharSequenceStreamOpContext<I>): (Stream<Char>) -> Stream<Char> {
        return streamOpContext.characterMapOperations.fold({ cStream: Stream<Char> -> cStream },
                                                           { func, op -> func.andThen(op) })
    }

    private fun composeCharacterSequenceMappingFunction(streamOpContext: CharSequenceStreamOpContext<I>): (Stream<CharSequence>) -> Stream<CharSequence> {
        return streamOpContext.segmentMapOperations.fold({ csStream: Stream<CharSequence> -> csStream },
                                                         { func, op -> func.andThen(op) })
    }

}