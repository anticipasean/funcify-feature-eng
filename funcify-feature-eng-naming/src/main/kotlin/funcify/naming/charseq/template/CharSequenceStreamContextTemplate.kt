package funcify.naming.charseq.template

import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.operation.DefaultCharSequenceOperationFactory
import funcify.naming.charseq.spliterator.DelimiterGroupingSpliterator
import funcify.naming.charseq.spliterator.MappingWithIndexSpliterator
import funcify.naming.charseq.spliterator.PairWindowMappingSpliterator
import funcify.naming.charseq.spliterator.SlidingListWindowMappingSpliterator
import funcify.naming.charseq.spliterator.TailFilterSpliterator
import funcify.naming.charseq.spliterator.TripleWindowMappingSpliterator
import kotlinx.collections.immutable.ImmutableList
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceStreamContextTemplate<I> : CharSequenceOperationContextTemplate<CharSequenceStreamContext<I>> {

    override fun filterLeadingCharacters(context: CharSequenceStreamContext<I>,
                                         filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        val leadingCharacterFilterOperations =
                context.leadingCharacterFilterOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs -> cs.filter(filter) })
        return context.copy(leadingCharacterFilterOperations = leadingCharacterFilterOperations)
    }

    override fun filterTrailingCharacters(context: CharSequenceStreamContext<I>,
                                          filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        val trailingCharacterFilterOperations =
                context.trailingCharacterFilterOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
                    StreamSupport.stream(TailFilterSpliterator(cs.spliterator(),
                                                               filter),
                                         cs.isParallel)
                })
        return context.copy(trailingCharacterFilterOperations = trailingCharacterFilterOperations)
    }

    override fun filterAnyCharacters(context: CharSequenceStreamContext<I>,
                                     filter: (Char) -> Boolean): CharSequenceStreamContext<I> {
        val allCharacterFilterOperations =
                context.allCharacterFilterOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs -> cs.filter(filter) })
        return context.copy(allCharacterFilterOperations = allCharacterFilterOperations)
    }

    override fun mapCharacters(context: CharSequenceStreamContext<I>,
                               mapper: (Char) -> Char): CharSequenceStreamContext<I> {
        val allCharacterMapOperations =
                context.allCharacterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs -> cs.map(mapper) })
        return context.copy(allCharacterMapOperations = allCharacterMapOperations)
    }

    override fun mapCharactersWithIndex(context: CharSequenceStreamContext<I>,
                                        mapper: (Int, Char) -> Char): CharSequenceStreamContext<I> {

        val allCharacterMapOperations = context.allCharacterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(MappingWithIndexSpliterator(cs.spliterator(),
                                                             mapper),
                                 cs.isParallel)
        })
        return context.copy(allCharacterMapOperations = allCharacterMapOperations)
    }

    override fun mapCharactersWithWindow(context: CharSequenceStreamContext<I>,
                                         windowSize: UInt,
                                         windowMapper: (ImmutableList<Char>) -> Char): CharSequenceStreamContext<I> {
        val allCharacterMapOperations = context.allCharacterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = cs.spliterator(),
                                                                     windowSize = windowSize.toInt(),
                                                                     windowMapper = windowMapper),
                                 cs.isParallel)
        })
        return context.copy(allCharacterMapOperations = allCharacterMapOperations)
    }

    override fun mapCharactersWithPairWindow(context: CharSequenceStreamContext<I>,
                                             windowMapper: (Pair<Char?, Char?>) -> Char): CharSequenceStreamContext<I> {
        val allCharacterMapOperations = context.allCharacterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = cs.spliterator()),
                                 cs.isParallel)
                    .map(windowMapper)
        })
        return context.copy(allCharacterMapOperations = allCharacterMapOperations)
    }

    override fun mapCharactersWithTripleWindow(context: CharSequenceStreamContext<I>,
                                               windowMapper: (Triple<Char?, Char, Char?>) -> Char): CharSequenceStreamContext<I> {
        val allCharacterMapOperations = context.allCharacterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = cs.spliterator()),
                                 cs.isParallel)
                    .map(windowMapper)
        })
        return context.copy(allCharacterMapOperations = allCharacterMapOperations)
    }

    override fun groupCharactersByDelimiter(context: CharSequenceStreamContext<I>,
                                            delimiter: Char): CharSequenceStreamContext<I> {
        val segmentingOperations = context.segmentingOperations.add(DefaultCharSequenceOperationFactory.createCharacterGroupingOperation { cs ->
            StreamSupport.stream(DelimiterGroupingSpliterator(cs.spliterator(),
                                                              { c -> c == delimiter }),
                                 false)
        })
        return context.copy(segmentingOperations = segmentingOperations)
    }

    override fun filterLeadingCharacterSequence(context: CharSequenceStreamContext<I>,
                                                filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        val segmentFilterOperations =
                context.segmentLeadingFilterOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
                    StreamSupport.stream(MappingWithIndexSpliterator(csi.spliterator(),
                                                                     { idx, cs ->
                                                                         if (idx == 0) {
                                                                             if (filter.invoke(cs)) {
                                                                                 Stream.of(cs)
                                                                             } else {
                                                                                 Stream.empty()
                                                                             }
                                                                         } else {
                                                                             Stream.of(cs)
                                                                         }
                                                                     }),
                                         csi.isParallel)
                            .flatMap { csStream -> csStream }
                })
        return context.copy(segmentLeadingFilterOperations = segmentFilterOperations)
    }

    override fun filterTrailingCharacterSequence(context: CharSequenceStreamContext<I>,
                                                 filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        val segmentFilterOperations =
                context.segmentTrailingFilterOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
                    StreamSupport.stream(TailFilterSpliterator(csi.spliterator(),
                                                               filter),
                                         false)
                })
        return context.copy(segmentTrailingFilterOperations = segmentFilterOperations)
    }

    override fun filterCharacterSequence(context: CharSequenceStreamContext<I>,
                                         filter: (CharSequence) -> Boolean): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            csi.filter(filter)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequence(context: CharSequenceStreamContext<I>,
                                      mapper: (CharSequence) -> CharSequence): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            csi.map(mapper)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithWindow(context: CharSequenceStreamContext<I>,
                                                windowSize: UInt,
                                                mapper: (ImmutableList<CharSequence>) -> CharSequence): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = csi.spliterator(),
                                                                     windowMapper = mapper,
                                                                     windowSize = windowSize.toInt()),
                                 csi.isParallel)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithPairWindow(context: CharSequenceStreamContext<I>,
                                                    mapper: (Pair<CharSequence?, CharSequence?>) -> CharSequence): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = csi.spliterator()),
                                 csi.isParallel)
                    .map(mapper)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithTripleWindow(context: CharSequenceStreamContext<I>,
                                                      mapper: (Triple<CharSequence?, CharSequence, CharSequence?>) -> CharSequence): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = csi.spliterator()),
                                 csi.isParallel)
                    .map(mapper)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithIndex(context: CharSequenceStreamContext<I>,
                                               mapper: (Int, CharSequence) -> CharSequence): CharSequenceStreamContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(MappingWithIndexSpliterator(csi.spliterator(),
                                                             mapper),
                                 csi.isParallel)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }
}