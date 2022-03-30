package funcify.naming.charseq.template

import funcify.naming.charseq.context.CharSequenceStreamFuncContext
import funcify.naming.charseq.extension.CharSequenceExtensions.spliterator
import funcify.naming.charseq.extension.CharSequenceExtensions.stream
import funcify.naming.charseq.group.LazyCharSequence
import funcify.naming.charseq.spliterator.MappingWithIndexSpliterator
import funcify.naming.charseq.spliterator.PairWindowMappingSpliterator
import funcify.naming.charseq.spliterator.SlidingListWindowMappingSpliterator
import funcify.naming.charseq.spliterator.TripleWindowMappingSpliterator
import funcify.naming.function.EitherStreamFunction
import funcify.naming.function.EitherStreamFunction.Companion.LeftStreamFunction
import funcify.naming.function.EitherStreamFunction.Companion.RightStreamFunction
import funcify.naming.function.FunctionExtensions.andThen
import kotlinx.collections.immutable.ImmutableList
import java.util.Objects
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.streams.asStream


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceStreamFunctionContextTemplate<I> : CharSequenceOperationContextTemplate<CharSequenceStreamFuncContext<I>> {

    companion object {

        private val DEFAULT_INSTANCE: CharSequenceStreamFunctionContextTemplate<Any?> by lazy {
            object : CharSequenceStreamFunctionContextTemplate<Any?> {

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

        internal fun <I> getDefaultInstance(): CharSequenceStreamFunctionContextTemplate<CharSequenceStreamFuncContext<I>> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as CharSequenceStreamFunctionContextTemplate<CharSequenceStreamFuncContext<I>>
        }
    }

    fun <I> transformIntoCharacterStream(context: CharSequenceStreamFuncContext<I>,
                                         transformer: (I) -> Stream<Char>): CharSequenceStreamFuncContext<I> {
        return context.copy(inputToCharSequenceTransformer = transformer.andThen { charStream ->
            Stream.of(LazyCharSequence(charStream.spliterator()))
        })
    }

    fun <I> transformIntoString(context: CharSequenceStreamFuncContext<I>,
                                transformer: (I) -> String): CharSequenceStreamFuncContext<I> {
        return context.copy(inputToCharSequenceTransformer = transformer.andThen { str -> Stream.of(str) })
    }

    fun <I> transformIntoStringIterable(context: CharSequenceStreamFuncContext<I>,
                                        transformer: (I) -> Iterable<String>): CharSequenceStreamFuncContext<I> {
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

    override fun emptyContext(): CharSequenceStreamFuncContext<I> {
        return CharSequenceStreamFuncContext(inputToCharSequenceTransformer = DEFAULT_INPUT_TRANSFORMER)
    }

    override fun filterCharacters(context: CharSequenceStreamFuncContext<I>,
                                  filter: (Char) -> Boolean): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { cStream: Stream<Char> ->
                        cStream.filter(filter)
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence -> cs.stream() }
                            .mapLeft { cStream -> cStream.filter(filter) }
                }
            }
        }
    }

    override fun mapCharacters(context: CharSequenceStreamFuncContext<I>,
                               mapper: (Char) -> CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { stream: Stream<Char> ->
                        stream.map(mapper)
                                .flatMap { cs: CharSequence -> cs.stream() }
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence ->
                        cs.stream()
                                .map(mapper)
                                .flatMap { resultCs: CharSequence -> resultCs.stream() }
                    }
                }
            }
        }
    }

    override fun mapCharactersWithIndex(context: CharSequenceStreamFuncContext<I>,
                                        mapper: (Int, Char) -> CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { cStream: Stream<Char> ->
                        StreamSupport.stream(MappingWithIndexSpliterator(sourceSpliterator = cStream.spliterator(),
                                                                         mapper = mapper),
                                             true)
                                .flatMap { cs: CharSequence -> cs.stream() }
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence ->
                        StreamSupport.stream(MappingWithIndexSpliterator(sourceSpliterator = cs.spliterator(),
                                                                         mapper = mapper),
                                             true)
                                .flatMap { resultCs: CharSequence -> resultCs.stream() }
                    }
                }
            }
        }
    }

    override fun mapCharactersWithWindow(context: CharSequenceStreamFuncContext<I>,
                                         windowSize: UInt,
                                         windowMapper: (ImmutableList<Char>) -> Char): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { cStream: Stream<Char> ->
                        StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = cStream.spliterator(),
                                                                                 windowSize = windowSize.toInt(),
                                                                                 windowMapper = windowMapper),
                                             false)
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence ->
                        StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = cs.spliterator(),
                                                                                 windowSize = windowSize.toInt(),
                                                                                 windowMapper = windowMapper),
                                             false)
                    }
                }
            }
        }
    }

    override fun mapCharactersWithPairWindow(context: CharSequenceStreamFuncContext<I>,
                                             windowMapper: (Pair<Char?, Char?>) -> Char): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { cStream: Stream<Char> ->
                        StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = cStream.spliterator()),
                                             false)
                                .map(windowMapper)
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence ->
                        StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = cs.spliterator()),
                                             false)
                                .map(windowMapper)
                    }
                }
            }
        }
    }

    override fun mapCharactersWithTripleWindow(context: CharSequenceStreamFuncContext<I>,
                                               windowMapper: (Triple<Char?, Char, Char?>) -> CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeft { cStream: Stream<Char> ->
                        StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = cStream.spliterator()),
                                             false)
                                .map(windowMapper)
                                .flatMap { cs: CharSequence -> cs.stream() }
                    }
                }
                is RightStreamFunction -> {
                    esf.mapRightToLeft { cs: CharSequence ->
                        StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = cs.spliterator()),
                                             false)
                                .map(windowMapper)
                                .flatMap { resultCS: CharSequence ->
                                    resultCS.stream()
                                }
                    }
                }
            }
        }
    }

    override fun groupCharactersByDelimiter(context: CharSequenceStreamFuncContext<I>,
                                            delimiter: Char): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                csStream.flatMap { cs: CharSequence ->
                                    cs.splitToSequence(delimiter)
                                            .asStream()
                                }
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        csStream.flatMap { cs: CharSequence ->
                            cs.splitToSequence(delimiter)
                                    .asStream()
                        }
                    }
                }
            }
        }
    }

    override fun filterCharacterSequence(context: CharSequenceStreamFuncContext<I>,
                                         filter: (CharSequence) -> Boolean): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                csStream.filter(filter)
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        csStream.filter(filter)
                    }
                }
            }
        }
    }

    override fun splitCharacterSequences(context: CharSequenceStreamFuncContext<I>,
                                         mapper: (CharSequence) -> Iterable<CharSequence>): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                csStream.flatMap { cs: CharSequence ->
                                    StreamSupport.stream(mapper.invoke(cs)
                                                                 .spliterator(),
                                                         true)
                                }
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        csStream.flatMap { cs: CharSequence ->
                            StreamSupport.stream(mapper.invoke(cs)
                                                         .spliterator(),
                                                 true)
                        }
                    }
                }
            }
        }
    }

    override fun mapCharacterSequence(context: CharSequenceStreamFuncContext<I>,
                                      mapper: (CharSequence) -> CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                csStream.map(mapper)
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        csStream.map(mapper)
                    }
                }
            }
        }
    }

    override fun mapCharacterSequenceWithWindow(context: CharSequenceStreamFuncContext<I>,
                                                windowSize: UInt,
                                                mapper: (ImmutableList<CharSequence>) -> Iterable<CharSequence>): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = csStream.spliterator(),
                                                                                         windowSize = windowSize.toInt(),
                                                                                         windowMapper = mapper),
                                                     false)
                                        .flatMap { csIter: Iterable<CharSequence> ->
                                            StreamSupport.stream(csIter.spliterator(),
                                                                 true)
                                        }
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        StreamSupport.stream(SlidingListWindowMappingSpliterator(inputSpliterator = csStream.spliterator(),
                                                                                 windowSize = windowSize.toInt(),
                                                                                 windowMapper = mapper),
                                             false)
                                .flatMap { csIter: Iterable<CharSequence> ->
                                    StreamSupport.stream(csIter.spliterator(),
                                                         true)
                                }
                    }
                }
            }
        }
    }

    override fun mapCharacterSequenceWithPairWindow(context: CharSequenceStreamFuncContext<I>,
                                                    mapper: (Pair<CharSequence?, CharSequence?>) -> Iterable<CharSequence>): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = csStream.spliterator()),
                                                     false)
                                        .map(mapper)
                                        .flatMap { csIter: Iterable<CharSequence> ->
                                            StreamSupport.stream(csIter.spliterator(),
                                                                 true)
                                        }
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        StreamSupport.stream(PairWindowMappingSpliterator(inputSpliterator = csStream.spliterator()),
                                             false)
                                .map(mapper)
                                .flatMap { csIter: Iterable<CharSequence> ->
                                    StreamSupport.stream(csIter.spliterator(),
                                                         true)
                                }
                    }
                }
            }
        }
    }

    override fun mapCharacterSequenceWithTripleWindow(context: CharSequenceStreamFuncContext<I>,
                                                      mapper: (Triple<CharSequence?, CharSequence, CharSequence?>) -> Iterable<CharSequence>): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = csStream.spliterator()),
                                                     false)
                                        .map(mapper)
                                        .flatMap { csIter: Iterable<CharSequence> ->
                                            StreamSupport.stream(csIter.spliterator(),
                                                                 true)
                                        }
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        StreamSupport.stream(TripleWindowMappingSpliterator(inputSpliterator = csStream.spliterator()),
                                             false)
                                .map(mapper)
                                .flatMap { csIter: Iterable<CharSequence> ->
                                    StreamSupport.stream(csIter.spliterator(),
                                                         true)
                                }
                    }
                }
            }
        }
    }

    override fun mapCharacterSequenceWithIndex(context: CharSequenceStreamFuncContext<I>,
                                               mapper: (Int, CharSequence) -> CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                StreamSupport.stream(MappingWithIndexSpliterator(sourceSpliterator = csStream.spliterator(),
                                                                                 mapper = mapper),
                                                     true)
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        StreamSupport.stream(MappingWithIndexSpliterator(sourceSpliterator = csStream.spliterator(),
                                                                         mapper = mapper),
                                             true)
                    }
                }
            }
        }
    }

    override fun prependCharacterSequence(context: CharSequenceStreamFuncContext<I>,
                                          charSequence: CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                Stream.concat(Stream.of(charSequence),
                                              csStream)
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        Stream.concat(Stream.of(charSequence),
                                      csStream)
                    }
                }
            }
        }
    }

    override fun appendCharacterSequence(context: CharSequenceStreamFuncContext<I>,
                                         charSequence: CharSequence): CharSequenceStreamFuncContext<I> {
        return context.update { esf: EitherStreamFunction<Char, CharSequence> ->
            when (esf) {
                is LeftStreamFunction -> {
                    esf.mapLeftToRight { cStream: Stream<Char> ->
                        cStream.reduce(StringBuilder(),
                                       StringBuilder::append,
                                       StringBuilder::append)
                    }
                            .mapRight { csStream: Stream<CharSequence> ->
                                Stream.concat(csStream,
                                              Stream.of(charSequence))
                            }
                }
                is RightStreamFunction -> {
                    esf.mapRight { csStream: Stream<CharSequence> ->
                        Stream.concat(csStream,
                                      Stream.of(charSequence))
                    }
                }
            }
        }
    }
}