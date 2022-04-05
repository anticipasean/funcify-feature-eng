package funcify.feature.naming.charseq.template


import funcify.feature.naming.charseq.context.CharSequenceStreamOpContext
import funcify.feature.naming.charseq.extension.CharSequenceExtensions.stream
import funcify.feature.naming.charseq.group.LazyCharSequence
import funcify.feature.naming.charseq.operation.DefaultCharSequenceOperationFactory
import funcify.feature.naming.charseq.spliterator.DelimiterGroupingSpliterator
import funcify.feature.naming.charseq.spliterator.MappingWithIndexSpliterator
import funcify.feature.naming.charseq.spliterator.PairWindowMappingSpliterator
import funcify.feature.naming.charseq.spliterator.SlidingListWindowMappingSpliterator
import funcify.feature.naming.charseq.spliterator.TripleWindowMappingSpliterator
import funcify.feature.naming.function.FunctionExtensions.andThen
import kotlinx.collections.immutable.ImmutableList
import java.util.Objects
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal interface CharSequenceStreamOperationContextTemplate<I> : CharSequenceOperationContextTemplate<CharSequenceStreamOpContext<I>> {

    companion object {

        private val DEFAULT_INSTANCE: CharSequenceStreamOperationContextTemplate<Any?> by lazy {
            object : CharSequenceStreamOperationContextTemplate<Any?> {

            }
        }

        private val DEFAULT_INPUT_TRANSFORMER: (Any?) -> Stream<CharSequence> by lazy {
            { input: Any? ->
                when (input) {
                    is CharSequence -> Stream.of(input)
                    is Collection<*> -> input.stream()
                            .filter(Objects::nonNull)
                    is Iterable<*> -> StreamSupport.stream(input.spliterator(),
                                                           false)
                            .filter(Objects::nonNull)
                    else -> Stream.ofNullable(input)
                }.map { a: Any? ->
                    a as? CharSequence
                    ?: a?.toString()
                    ?: ""
                }
            }
        }

        internal fun <I> getDefaultInstance(): CharSequenceOperationContextTemplate<CharSequenceStreamOpContext<I>> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as CharSequenceStreamOperationContextTemplate<I>
        }
    }

    fun <I> transformIntoCharacterStream(context: CharSequenceStreamOpContext<I>,
                                         transformer: (I) -> Stream<Char>): CharSequenceStreamOpContext<I> {
        return context.copy(inputToCharSequenceTransformer = transformer.andThen { charStream ->
            Stream.of(LazyCharSequence(charStream.spliterator()))
        })
    }

    fun <I> transformIntoString(context: CharSequenceStreamOpContext<I>,
                                transformer: (I) -> String): CharSequenceStreamOpContext<I> {
        return context.copy(inputToCharSequenceTransformer = transformer.andThen { str -> Stream.of(str) })
    }

    fun <I> transformIntoStringIterable(context: CharSequenceStreamOpContext<I>,
                                        transformer: (I) -> Iterable<String>): CharSequenceStreamOpContext<I> {
        return context.copy(inputToCharSequenceTransformer = transformer.andThen { strs ->
            when (strs) {
                is Collection<String> -> strs.stream()
                        .map { s -> s }
                else -> StreamSupport.stream(strs.spliterator(),
                                             false)
                        .map { s -> s }
            }
        })
    }

    override fun emptyContext(): CharSequenceStreamOpContext<I> {
        return CharSequenceStreamOpContext<I>(inputToCharSequenceTransformer = DEFAULT_INPUT_TRANSFORMER)
    }

    override fun filterCharacters(context: CharSequenceStreamOpContext<I>,
                                  filter: (Char) -> Boolean): CharSequenceStreamOpContext<I> {
        val characterMapOperations =
                context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs -> cs.filter(filter) })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun mapCharacters(context: CharSequenceStreamOpContext<I>,
                               mapper: (Char) -> CharSequence): CharSequenceStreamOpContext<I> {
        val characterMapOperations =
                context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { charStream ->
                    charStream.map(mapper)
                            .flatMap { charSeq -> charSeq.stream() }
                })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun mapCharactersWithIndex(context: CharSequenceStreamOpContext<I>,
                                        mapper: (Int, Char) -> CharSequence): CharSequenceStreamOpContext<I> {

        val characterMapOperations = context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { charStream ->
            StreamSupport.stream(MappingWithIndexSpliterator(charStream.spliterator(),
                                                             mapper),
                                 charStream.isParallel)
                    .flatMap { charSeq -> charSeq.stream() }
        })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun mapCharactersWithWindow(context: CharSequenceStreamOpContext<I>,
                                         windowSize: UInt,
                                         windowMapper: (ImmutableList<Char>) -> Char): CharSequenceStreamOpContext<I> {
        val characterMapOperations = context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = cs.spliterator(),
                                                                     windowSize = windowSize.toInt(),
                                                                     windowMapper = windowMapper),
                                 cs.isParallel)
        })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun mapCharactersWithPairWindow(context: CharSequenceStreamOpContext<I>,
                                             windowMapper: (Pair<Char?, Char?>) -> Char): CharSequenceStreamOpContext<I> {
        val characterMapOperations = context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { cs ->
            StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = cs.spliterator()),
                                 cs.isParallel)
                    .map(windowMapper)
        })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun mapCharactersWithTripleWindow(context: CharSequenceStreamOpContext<I>,
                                               windowMapper: (Triple<Char?, Char, Char?>) -> CharSequence): CharSequenceStreamOpContext<I> {
        val characterMapOperations = context.characterMapOperations.add(DefaultCharSequenceOperationFactory.createCharacterMapOperation { charStream ->
            StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = charStream.spliterator()),
                                 charStream.isParallel)
                    .map(windowMapper)
                    .flatMap { charSeq -> charSeq.stream() }
        })
        return context.copy(characterMapOperations = characterMapOperations)
    }

    override fun groupCharactersByDelimiter(context: CharSequenceStreamOpContext<I>,
                                            delimiter: Char): CharSequenceStreamOpContext<I> {
        val segmentingOperations = context.segmentingOperations.add(DefaultCharSequenceOperationFactory.createCharacterGroupingOperation { cs ->
            StreamSupport.stream(DelimiterGroupingSpliterator(cs.spliterator(),
                                                              { c -> c == delimiter }),
                                 false)
        })
        return context.copy(segmentingOperations = segmentingOperations)
    }

    override fun splitCharacterSequences(context: CharSequenceStreamOpContext<I>,
                                         mapper: (CharSequence) -> Iterable<CharSequence>): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            csi.flatMap { cs ->
                StreamSupport.stream(mapper.invoke(cs)
                                             .spliterator(),
                                     false)
            }
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun filterCharacterSequence(context: CharSequenceStreamOpContext<I>,
                                         filter: (CharSequence) -> Boolean): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            csi.filter(filter)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequence(context: CharSequenceStreamOpContext<I>,
                                      mapper: (CharSequence) -> CharSequence): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            csi.map(mapper)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithWindow(context: CharSequenceStreamOpContext<I>,
                                                windowSize: UInt,
                                                mapper: (ImmutableList<CharSequence>) -> Iterable<CharSequence>): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = csi.spliterator(),
                                                                     windowMapper = mapper,
                                                                     windowSize = windowSize.toInt()),
                                 csi.isParallel)
                    .flatMap { csIterable ->
                        StreamSupport.stream(csIterable.spliterator(),
                                             false)
                    }
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithPairWindow(context: CharSequenceStreamOpContext<I>,
                                                    mapper: (Pair<CharSequence?, CharSequence?>) -> Iterable<CharSequence>): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = csi.spliterator()),
                                 csi.isParallel)
                    .map(mapper)
                    .flatMap { csIterable ->
                        StreamSupport.stream(csIterable.spliterator(),
                                             false)
                    }
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithTripleWindow(context: CharSequenceStreamOpContext<I>,
                                                      mapper: (Triple<CharSequence?, CharSequence, CharSequence?>) -> Iterable<CharSequence>): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = csi.spliterator()),
                                 csi.isParallel)
                    .map(mapper)
                    .flatMap { csIterable ->
                        StreamSupport.stream(csIterable.spliterator(),
                                             false)
                    }
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun mapCharacterSequenceWithIndex(context: CharSequenceStreamOpContext<I>,
                                               mapper: (Int, CharSequence) -> CharSequence): CharSequenceStreamOpContext<I> {
        val segmentOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            StreamSupport.stream(MappingWithIndexSpliterator(csi.spliterator(),
                                                             mapper),
                                 csi.isParallel)
        })
        return context.copy(segmentMapOperations = segmentOperations)
    }

    override fun prependCharacterSequence(context: CharSequenceStreamOpContext<I>,
                                          charSequence: CharSequence): CharSequenceStreamOpContext<I> {
        val segmentMapOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            Stream.concat(Stream.of(charSequence),
                          csi)
        })
        return context.copy(segmentMapOperations = segmentMapOperations)
    }

    override fun appendCharacterSequence(context: CharSequenceStreamOpContext<I>,
                                         charSequence: CharSequence): CharSequenceStreamOpContext<I> {
        val segmentMapOperations = context.segmentMapOperations.add(DefaultCharSequenceOperationFactory.createCharSequenceMapOperation { csi ->
            Stream.concat(csi,
                          Stream.of(charSequence))
        })
        return context.copy(segmentMapOperations = segmentMapOperations)
    }
}