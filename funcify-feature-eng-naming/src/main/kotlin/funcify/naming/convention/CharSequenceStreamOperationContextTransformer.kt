package funcify.naming.convention

import funcify.naming.charseq.extension.CharSequenceExtensions.stream
import funcify.naming.charseq.group.LazyCharSequence
import funcify.naming.charseq.operation.CharSequenceMapOperation
import funcify.naming.charseq.operation.CharSequenceOperation
import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.operation.CharacterGroupFlatteningOperation
import funcify.naming.charseq.operation.CharacterGroupingOperation
import funcify.naming.charseq.operation.CharacterMapOperation
import funcify.naming.function.EitherFunction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream

internal class CharSequenceStreamOperationContextTransformer<I : Any> : (CharSequenceStreamContext<I>) -> (I) -> ImmutableList<String> {

    companion object {

        private val DEFAULT_INSTANCE: CharSequenceStreamOperationContextTransformer<Any> by lazy {
            CharSequenceStreamOperationContextTransformer<Any>()
        }

        fun <I : Any> getInstance(): CharSequenceStreamOperationContextTransformer<I> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as CharSequenceStreamOperationContextTransformer<I>
        }
    }

    override fun invoke(streamOpContext: CharSequenceStreamContext<I>): (I) -> ImmutableList<String> {
        return { input: I ->
            val charSequenceStream: Stream<CharSequence> = streamOpContext.inputToCharSequenceTransformer.invoke(input)
            sequenceOf(streamOpContext.allCharacterFilterOperations,
                       streamOpContext.allCharacterMapOperations,
                       streamOpContext.leadingCharacterFilterOperations,
                       streamOpContext.leadingCharacterMapOperations,
                       streamOpContext.trailingCharacterFilterOperations,
                       streamOpContext.trailingCharacterMapOperations,
                       streamOpContext.segmentingOperations,
                       streamOpContext.segmentFilterOperations,
                       streamOpContext.segmentLeadingFilterOperations,
                       streamOpContext.segmentTrailingFilterOperations,
                       streamOpContext.segmentMapOperations).fold(createCharSequenceFunctionContext(),
                                                                  ::charSequenceFunctionContextOperationFold)
                    .fold({ function: (Stream<CharSequence>) -> Stream<Char> ->
                              Stream.of(LazyCharSequence(function.invoke(charSequenceStream)
                                                                 .spliterator()))
                          },
                          { function: (Stream<CharSequence>) -> Stream<CharSequence> ->
                              function.invoke(charSequenceStream)
                          })
                    .map { cs -> cs.toString() }
                    .reduce(persistentListOf(),
                            { pl, ns -> pl.add(ns) },
                            { pl1, pl2 -> pl1.addAll(pl2) })
        }
    }

    private fun charSequenceFunctionContextOperationFold(eitherFunc: EitherFunction<Stream<Char>, Stream<CharSequence>>,
                                                         opList: ImmutableList<CharSequenceOperation<Stream<Char>, Stream<CharSequence>>>): EitherFunction<Stream<Char>, Stream<CharSequence>> {
        if (opList.isEmpty()) {
            return eitherFunc
        }
        return opList.fold(eitherFunc) { ef, op ->
            when (op) {
                is CharSequenceMapOperation -> {
                    ef.mapToRightResult({ cStream -> op.invoke(Stream.of(LazyCharSequence(cStream.spliterator()))) },
                                        { csStream -> op.invoke(csStream) })
                }
                is CharacterGroupFlatteningOperation -> {
                    ef.mapToLeftResult({ cStream -> cStream },
                                       { csStream -> op.invoke(csStream) })
                }
                is CharacterGroupingOperation -> {
                    ef.mapToRightResult({ cStream -> op.invoke(cStream) },
                                        { csStream -> csStream })
                }
                is CharacterMapOperation -> {
                    ef.mapToLeftResult({ cStream -> op.invoke(cStream) },
                                       { csStream ->
                                           op.invoke(csStream.flatMap { cs ->
                                               cs.stream()
                                           })
                                       })
                }
            }
        }
    }
    
    private fun createCharSequenceFunctionContext(): EitherFunction<Stream<Char>, Stream<CharSequence>> {
        return EitherFunction.ofRightResult { csi: Stream<CharSequence> -> csi }
    }

}