package funcify.naming.convention

import funcify.naming.charseq.extension.CharSequenceExtensions.spliterator
import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.spliterator.TripleWindowMappingSpliterator
import funcify.naming.charseq.template.CharSequenceOperationContextTemplate
import funcify.naming.charseq.template.CharSequenceStreamContextTemplate
import funcify.naming.convention.NamingConventionFactory.AllCharacterSpec
import funcify.naming.convention.NamingConventionFactory.CompleteWindowSpec
import funcify.naming.convention.NamingConventionFactory.ConventionSpec
import funcify.naming.convention.NamingConventionFactory.FullTransformationSpec
import funcify.naming.convention.NamingConventionFactory.InputSpec
import funcify.naming.convention.NamingConventionFactory.LeadingCharactersSpec
import funcify.naming.convention.NamingConventionFactory.OutputSpec
import funcify.naming.convention.NamingConventionFactory.StringExtractionSpec
import funcify.naming.convention.NamingConventionFactory.StringTransformationSpec
import funcify.naming.convention.NamingConventionFactory.TrailingCharactersSpec
import funcify.naming.convention.NamingConventionFactory.WindowActionSpec
import funcify.naming.convention.NamingConventionFactory.WindowRangeCloseSpec
import funcify.naming.convention.NamingConventionFactory.WindowRangeOpenSpec
import funcify.naming.function.FunctionExtensions.andThen
import funcify.naming.function.FunctionExtensions.negate
import funcify.naming.impl.DefaultConventionalName
import kotlinx.collections.immutable.ImmutableList
import java.util.Deque
import java.util.LinkedList
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
internal class DefaultNamingConventionFactory() : NamingConventionFactory {

    companion object {

        private val DEFAULT_INSTANCE: NamingConventionFactory by lazy { DefaultNamingConventionFactory() }

        fun getInstance(): NamingConventionFactory {
            return DEFAULT_INSTANCE
        }

        private fun <I, CTX, R> CharSequenceOperationContextTemplate<CTX>.streamContextFold(context: CTX,
                                                                                            function: (CharSequenceStreamContextTemplate<I>, CharSequenceStreamContext<I>) -> R): R {
            when (this) {
                is CharSequenceStreamContextTemplate<*> -> {
                    when (context) {
                        is CharSequenceStreamContext<*> -> {
                            @Suppress("UNCHECKED_CAST") //
                            return function.invoke(this as CharSequenceStreamContextTemplate<I>,
                                                   context as CharSequenceStreamContext<I>)
                        }
                        else -> {
                            throw IllegalArgumentException("unhandled context type: ${context?.let { it::class.qualifiedName }}")
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("unhandled template type: ${this::class.qualifiedName}")
                }
            }
        }

        private fun <I, CTX> CharSequenceOperationContextTemplate<CTX>.streamContextApply(context: CTX,
                                                                                          function: (CharSequenceStreamContextTemplate<I>, CharSequenceStreamContext<I>) -> CharSequenceStreamContext<I>): CTX {
            return streamContextFold<I, CTX, CTX>(context,
                                                  function.andThen { ctx ->
                                                      @Suppress("UNCHECKED_CAST") //
                                                      ctx as CTX
                                                  })
        }

    }

    override fun createConventionForRawStrings(): InputSpec<String> {
        val template = CharSequenceStreamContextTemplate.getDefaultInstance<String>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    override fun <I : Any> createConventionFor(): InputSpec<I> {
        val template = CharSequenceStreamContextTemplate.getDefaultInstance<I>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    internal class DefaultInputSpec<I : Any, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                  val charSeqOpContext: CTX) : InputSpec<I> {

        override fun whenInputProvided(extraction: StringExtractionSpec<I>.() -> Unit): OutputSpec<I> {
            val stringExtractionSpec = DefaultStringExtractionSpec<I, CTX>(charSeqOpTemplate,
                                                                           charSeqOpContext)
            extraction.invoke(stringExtractionSpec)
            return DefaultOutputSpec<I, CTX>(charSeqOpTemplate,
                                             stringExtractionSpec.charSeqOpContext)
        }
    }

    internal class DefaultStringExtractionSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                       var charSeqOpContext: CTX) : StringExtractionSpec<I> {

        override fun extractOneOrMoreSegmentsWith(function: (I) -> Iterable<String>) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.transformIntoStringIterable(ctx,
                                              function)
            }
        }

        override fun treatAsOneSegment(function: (I) -> String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.transformIntoString(ctx,
                                      function)
            }
        }

    }

    internal class DefaultOutputSpec<I : Any, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                   val charSeqOpContext: CTX) : OutputSpec<I> {

        override fun followConvention(transformation: FullTransformationSpec.() -> Unit): ConventionSpec<I> {
            val fullTransformationSpec = DefaultFullTransformationSpec<I, CTX>(charSeqOpTemplate,
                                                                               charSeqOpContext)
            transformation.invoke(fullTransformationSpec)
            return DefaultConventionSpec<I, CTX>(charSeqOpTemplate,
                                                 fullTransformationSpec.charSeqOpContext,
                                                 fullTransformationSpec.delimiter)
        }

    }

    internal class DefaultFullTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                         var charSeqOpContext: CTX,
                                                         var delimiter: String = "") : FullTransformationSpec {

        override fun joinSegmentsWith(inputDelimiter: Char) {
            delimiter = inputDelimiter.toString()
        }

        override fun joinSegmentsWithoutAnyDelimiter() {
            delimiter = ""
        }

        override fun forEachSegment(transformation: StringTransformationSpec.() -> Unit) {
            val stringTransformationSpec = DefaultStringTransformationSpec<I, CTX>(charSeqOpTemplate,
                                                                                   charSeqOpContext)
            transformation.invoke(stringTransformationSpec)
            charSeqOpContext = stringTransformationSpec.charSeqOpContext
        }

    }

    internal class DefaultStringTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                           var charSeqOpContext: CTX) : StringTransformationSpec {

        override fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> Unit) {
            val leadingCharactersSpec = DefaultLeadingCharactersSpec<I, CTX>(charSeqOpTemplate,
                                                                             charSeqOpContext)
            transformation.invoke(leadingCharactersSpec)
            charSeqOpContext = leadingCharactersSpec.charSeqOpContext
        }

        override fun forAnyCharacter(transformation: AllCharacterSpec.() -> Unit) {
            val allCharactersSpec = DefaultAllCharacterSpec<I, CTX>(charSeqOpTemplate,
                                                                    charSeqOpContext)
            transformation.invoke(allCharactersSpec)
            charSeqOpContext = allCharactersSpec.charSeqOpContext
        }

        override fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> Unit) {
            val trailingCharactersSpec = DefaultTrailingCharactersSpec<I, CTX>(charSeqOpTemplate,
                                                                               charSeqOpContext)
            transformation.invoke(trailingCharactersSpec)
            charSeqOpContext = trailingCharactersSpec.charSeqOpContext
        }

        override fun replace(regex: Regex,
                             replacement: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    cs.replace(regex,
                               replacement)
                }
            }
        }

        override fun replace(regex: Regex,
                             transform: (MatchResult) -> CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    cs.replace(regex,
                               transform)
                }
            }
        }

        override fun prepend(prefix: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    prefix + cs
                }
            }
        }

        override fun append(suffix: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    cs.toString() + suffix
                }
            }
        }

        override fun transformAll(transformer: (String) -> String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    transformer.invoke(cs.toString())
                }
            }
        }


    }

    internal class DefaultLeadingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                        var charSeqOpContext: CTX) : LeadingCharactersSpec {

        override fun stripAny(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.filterLeadingCharacters(ctx,
                                          condition.negate())
            }
        }

        override fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                           function: (Char) -> String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapLeadingCharacterSequence(ctx) { cs: CharSequence ->
                    if (cs.isNotEmpty() && condition.invoke(cs.first())) {
                        function.invoke(cs.first()) + if (cs.length > 1) {
                            cs.slice(1 until cs.length)
                        } else {
                            ""
                        }
                    } else {
                        cs
                    }
                }
            }
        }

        override fun prependToFirstSegment(prefix: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapLeadingCharacterSequence(ctx) { cs: CharSequence ->
                    prefix + cs
                }
            }
        }

        override fun prependSegment(segment: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.prependCharacterSequence(ctx,
                                           segment)
            }
        }

    }

    internal class DefaultTrailingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                         var charSeqOpContext: CTX) : TrailingCharactersSpec {

        override fun stripAny(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.filterTrailingCharacters(ctx,
                                           condition.negate())
            }
        }

        override fun replaceLastCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                         function: (Char) -> String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapTrailingCharacterSequence(ctx) { cs: CharSequence ->
                    if (cs.isNotEmpty() && condition.invoke(cs.last())) {
                        "${
                            if (cs.length > 1) {
                                cs.slice(0 until cs.length - 1)
                            } else {
                                ""
                            }
                        }" + function.invoke(cs.last())
                    } else {
                        cs
                    }
                }
            }
        }

        override fun appendToLastSegment(suffix: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapTrailingCharacterSequence(ctx) { cs ->
                    cs.toString() + suffix
                }
            }
        }

        override fun appendSegment(segment: String) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.appendCharacterSequence(ctx,
                                          segment)
            }
        }

    }

    internal class DefaultAllCharacterSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                   var charSeqOpContext: CTX) : AllCharacterSpec {

        override fun removeAny(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.filterAnyCharacters(ctx,
                                      condition.negate<Char>())
            }
        }

        override fun transformIf(condition: (Char) -> Boolean,
                                 transformer: (Char) -> Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacters(ctx) { c: Char ->
                    if (condition.invoke(c)) {
                        transformer.invoke(c)
                    } else {
                        c
                    }
                }
            }
        }

        override fun transformAll(transformer: (Char) -> Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacters(ctx,
                                transformer)
            }
        }

        override fun transformByPosition(window: WindowRangeOpenSpec.() -> CompleteWindowSpec) {
            when (val completeWindowSpec = window.invoke(DefaultWindowRangeOpenSpec())) {
                is DefaultCompleteWindowTransformationSpec -> {
                    if (completeWindowSpec.precededByCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharactersWithTripleWindow(ctx) { charTriple: Triple<Char?, Char, Char?> ->
                                if (charTriple.first != null && completeWindowSpec.startCondition.invoke(charTriple.second) && completeWindowSpec.precededByCondition.invoke(charTriple.first!!)) {
                                    completeWindowSpec.transformer.invoke(charTriple.second)
                                } else {
                                    charTriple.second.toString()
                                }
                            }
                        }
                    } else if (completeWindowSpec.followedByCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharactersWithTripleWindow(ctx) { charTriple: Triple<Char?, Char, Char?> ->
                                if (charTriple.third != null && completeWindowSpec.startCondition.invoke(charTriple.second) && completeWindowSpec.followedByCondition.invoke(charTriple.third!!)) {
                                    completeWindowSpec.transformer.invoke(charTriple.second)
                                } else {
                                    charTriple.second.toString()
                                }

                            }
                        }
                    } else {
                        throw IllegalArgumentException("no preceded_by or followed_by condition was supplied in ${CompleteWindowSpec::class.qualifiedName}")
                    }
                }
                is DefaultCompleteWindowSplitSpec -> {
                    if (completeWindowSpec.precededByCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.splitCharacterSequences(ctx) { cs: CharSequence ->
                                StreamSupport.stream(TripleWindowMappingSpliterator(cs.spliterator()),
                                                     false)
                                        .reduce((LinkedList<StringBuilder>() as Deque<StringBuilder>).apply { this.offerLast(StringBuilder()) },
                                                { sbq, charTriple ->
                                                    if (charTriple.first != null && completeWindowSpec.startCondition.invoke(charTriple.second) && completeWindowSpec.precededByCondition.invoke(charTriple.first!!)) {
                                                        sbq.offerLast(StringBuilder().append(completeWindowSpec.transformer.invoke(charTriple.second)))
                                                        sbq
                                                    } else {
                                                        sbq.peekLast()
                                                                .append(charTriple.second.toString())
                                                        sbq
                                                    }
                                                },
                                                { sbq1, sbq2 ->
                                                    sbq1.addAll(sbq2)
                                                            .let { sbq1 }
                                                })

                            }
                        }
                    } else if (completeWindowSpec.followedByCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.splitCharacterSequences(ctx) { cs: CharSequence ->
                                StreamSupport.stream(TripleWindowMappingSpliterator(cs.spliterator()),
                                                     false)
                                        .reduce((LinkedList<StringBuilder>() as Deque<StringBuilder>).apply { this.offerLast(StringBuilder()) },
                                                { sbq, charTriple ->
                                                    if (charTriple.third != null && completeWindowSpec.startCondition.invoke(charTriple.second) && completeWindowSpec.followedByCondition.invoke(charTriple.third!!)) {
                                                        sbq.peekLast()
                                                                .append(completeWindowSpec.transformer.invoke(charTriple.second))
                                                        sbq.offerLast(StringBuilder())
                                                        sbq
                                                    } else {
                                                        sbq.peekLast()
                                                                .append(charTriple.second.toString())
                                                        sbq
                                                    }
                                                },
                                                { sbq1, sbq2 ->
                                                    sbq1.addAll(sbq2)
                                                            .let { sbq1 }
                                                })

                            }
                        }
                    } else {
                        throw IllegalArgumentException("no preceded_by or followed_by condition was supplied in ${CompleteWindowSpec::class.qualifiedName}")
                    }
                }
                else -> {
                    throw IllegalStateException("complete_window_spec type not supported: ${completeWindowSpec::class.qualifiedName}")
                }
            }
        }

    }

    internal class DefaultWindowRangeOpenSpec : WindowRangeOpenSpec {

        override fun anyCharacter(startCharacterCondition: (Char) -> Boolean): WindowRangeCloseSpec {
            return DefaultWindowRangeCloseSpec(startCondition = startCharacterCondition)
        }

    }

    internal class DefaultWindowRangeCloseSpec(val startCondition: (Char) -> Boolean) : WindowRangeCloseSpec {

        override fun precededBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            return DefaultWindowActionSpec(startCondition = startCondition,
                                           precededByCondition = endCharacterCondition)
        }

        override fun followedBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            return DefaultWindowActionSpec(startCondition = startCondition,
                                           followedByCondition = endCharacterCondition)
        }

    }

    internal class DefaultWindowActionSpec(val startCondition: (Char) -> Boolean,
                                           val precededByCondition: ((Char) -> Boolean)? = null,
                                           val followedByCondition: ((Char) -> Boolean)? = null) : WindowActionSpec {

        override fun transformInto(function: (Char) -> CharSequence): CompleteWindowSpec {
            return DefaultCompleteWindowTransformationSpec(startCondition = startCondition,
                                                           precededByCondition = precededByCondition,
                                                           followedByCondition = followedByCondition,
                                                           transformer = function)
        }

        override fun splitIntoSeparateSegments(): CompleteWindowSpec {
            return DefaultCompleteWindowSplitSpec(startCondition = startCondition,
                                                  precededByCondition = precededByCondition,
                                                  followedByCondition = followedByCondition)
        }

        override fun transformAndSplitIntoSeparateSegments(function: (Char) -> CharSequence): CompleteWindowSpec {
            return DefaultCompleteWindowSplitSpec(startCondition = startCondition,
                                                  precededByCondition = precededByCondition,
                                                  followedByCondition = followedByCondition,
                                                  transformer = function)
        }

    }

    internal class DefaultCompleteWindowTransformationSpec(val startCondition: (Char) -> Boolean,
                                                           val precededByCondition: ((Char) -> Boolean)? = null,
                                                           val followedByCondition: ((Char) -> Boolean)? = null,
                                                           val transformer: (Char) -> CharSequence = { c -> c.toString() }) : CompleteWindowSpec {}

    internal class DefaultCompleteWindowSplitSpec(val startCondition: (Char) -> Boolean,
                                                  val precededByCondition: ((Char) -> Boolean)? = null,
                                                  val followedByCondition: ((Char) -> Boolean)? = null,
                                                  val transformer: (Char) -> CharSequence = { c -> c.toString() }) : CompleteWindowSpec {}


    internal class DefaultConventionSpec<I : Any, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                       val charSeqOpContext: CTX,
                                                       val delimiter: String) : ConventionSpec<I> {

        override fun named(conventionName: String): NamingConvention<I> {
            val inputTransformer = charSeqOpTemplate.streamContextFold<I, CTX, (I) -> ImmutableList<String>>(charSeqOpContext) { _, ctx ->
                CharSequenceStreamOperationContextTransformer.getInstance<I>()
                        .invoke(ctx)
            }
                    .andThen { strs ->
                        DefaultConventionalName(namingConventionKey = conventionName,
                                                rawStringNameSegments = strs,
                                                delimiter = delimiter)
                    }
            return DefaultNamingConvention<I>(conventionName = conventionName,
                                              derivationFunction = inputTransformer)
        }

        override fun namedAndIdentifiedBy(conventionName: String,
                                          conventionKey: Any): NamingConvention<I> {
            val inputTransformer = charSeqOpTemplate.streamContextFold<I, CTX, (I) -> ImmutableList<String>>(charSeqOpContext) { _, ctx ->
                CharSequenceStreamOperationContextTransformer.getInstance<I>()
                        .invoke(ctx)
            }
                    .andThen { strs ->
                        DefaultConventionalName(namingConventionKey = conventionKey,
                                                rawStringNameSegments = strs,
                                                delimiter = delimiter)
                    }
            return DefaultNamingConvention<I>(conventionName = conventionName,
                                              conventionKey = conventionKey,
                                              derivationFunction = inputTransformer)
        }
    }

}