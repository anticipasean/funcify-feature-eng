package funcify.naming.convention

import funcify.naming.NameSegment
import funcify.naming.charseq.group.LazyCharSequence
import funcify.naming.charseq.operation.CharSeqFunctionContext
import funcify.naming.charseq.operation.CharSequenceMapOperation
import funcify.naming.charseq.operation.CharSequenceOperation
import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.operation.CharacterGroupFlatteningOperation
import funcify.naming.charseq.operation.CharacterGroupingOperation
import funcify.naming.charseq.operation.CharacterMapOperation
import funcify.naming.impl.DefaultNameSegment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.Spliterators
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class CharSequenceStreamOperationContextTransformer<I : Any> : (CharSequenceStreamContext<I>) -> (I) -> ImmutableList<NameSegment> {

    override fun invoke(streamOpContext: CharSequenceStreamContext<I>): (I) -> ImmutableList<NameSegment> {
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
                    .map { cs -> DefaultNameSegment(cs.toString()) }
                    .reduce(persistentListOf(),
                            { pl, ns -> pl.add(ns) },
                            { pl1, pl2 -> pl1.addAll(pl2) })
        }
    }

    private fun charSequenceFunctionContextOperationFold(funcContext: CharSeqFunctionContext<Stream<Char>, Stream<CharSequence>>,
                                                         opList: ImmutableList<CharSequenceOperation<Stream<Char>, Stream<CharSequence>>>): CharSeqFunctionContext<Stream<Char>, Stream<CharSequence>> {
        if (opList.isEmpty()) {
            return funcContext
        }
        return opList.fold(funcContext) { ctx, op ->
            when (op) {
                is CharSequenceMapOperation -> {
                    ctx.mapCharSeqIterable({ cStream -> op.invoke(Stream.of(LazyCharSequence(cStream.spliterator()))) },
                                           { csStream -> op.invoke(csStream) })
                }
                is CharacterGroupFlatteningOperation -> {
                    ctx.mapCharSeq({ cStream -> cStream },
                                   { csStream -> op.invoke(csStream) })
                }
                is CharacterGroupingOperation -> {
                    ctx.mapCharSeqIterable({ cStream -> op.invoke(cStream) },
                                           { csStream -> csStream })
                }
                is CharacterMapOperation -> {
                    ctx.mapCharSeq({ cStream -> op.invoke(cStream) },
                                   { csStream ->
                                       op.invoke(csStream.flatMap { cs ->
                                           StreamSupport.stream(Spliterators.spliterator(cs.iterator(),
                                                                                         cs.length.toLong(),
                                                                                         IMMUTABLE and SIZED and NONNULL and ORDERED),
                                                                false)
                                       })
                                   })
                }
            }
        }
    }


    private fun createCharSequenceFunctionContext(): CharSeqFunctionContext<Stream<Char>, Stream<CharSequence>> {
        return CharSeqFunctionContext.ofCharSeqIterableFunction { csi: Stream<CharSequence> -> csi }
    }

}