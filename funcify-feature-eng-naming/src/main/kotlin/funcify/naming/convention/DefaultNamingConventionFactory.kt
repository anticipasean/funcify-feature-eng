package funcify.naming.convention

import funcify.naming.ConventionalName
import funcify.naming.NamingConvention
import funcify.naming.NamingConventionFactory
import funcify.naming.NamingConventionFactory.AllCharacterSpec
import funcify.naming.NamingConventionFactory.CharSequenceWindowActionSpec
import funcify.naming.NamingConventionFactory.CharSequenceWindowRangeCloseSpec
import funcify.naming.NamingConventionFactory.CharSequenceWindowRangeOpenSpec
import funcify.naming.NamingConventionFactory.CharacterWindowActionSpec
import funcify.naming.NamingConventionFactory.CharacterWindowRangeCloseSpec
import funcify.naming.NamingConventionFactory.CharacterWindowRangeOpenSpec
import funcify.naming.NamingConventionFactory.CompleteCharSequenceWindowSpec
import funcify.naming.NamingConventionFactory.CompleteCharacterWindowSpec
import funcify.naming.NamingConventionFactory.ConventionSpec
import funcify.naming.NamingConventionFactory.DelimiterSpec
import funcify.naming.NamingConventionFactory.FirstSegmentTransformationSpec
import funcify.naming.NamingConventionFactory.InputMappingSpec
import funcify.naming.NamingConventionFactory.InputSpec
import funcify.naming.NamingConventionFactory.LastSegmentTransformationSpec
import funcify.naming.NamingConventionFactory.LeadingCharactersSpec
import funcify.naming.NamingConventionFactory.OutputSpec
import funcify.naming.NamingConventionFactory.SegmentTransformationSpec
import funcify.naming.NamingConventionFactory.StringExtractionSpec
import funcify.naming.NamingConventionFactory.StringTransformationSpec
import funcify.naming.NamingConventionFactory.TrailingCharactersSpec
import funcify.naming.charseq.context.CharSequenceStreamFuncContext
import funcify.naming.charseq.context.CharSequenceStreamOpContext
import funcify.naming.charseq.template.CharSequenceOperationContextTemplate
import funcify.naming.charseq.template.CharSequenceStreamFunctionContextTemplate
import funcify.naming.charseq.template.CharSequenceStreamOperationContextTemplate
import funcify.naming.function.EitherStreamFunction.Companion.LeftStreamFunction
import funcify.naming.function.EitherStreamFunction.Companion.RightStreamFunction
import funcify.naming.function.FunctionExtensions.andThen
import funcify.naming.function.FunctionExtensions.negate
import funcify.naming.impl.DefaultConventionalName
import funcify.naming.impl.RawStringInputConventionalName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Spliterators
import java.util.stream.Stream


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
                                                                                            function: (CharSequenceStreamFunctionContextTemplate<I>, CharSequenceStreamFuncContext<I>) -> R): R {
            when (this) {
                is CharSequenceStreamFunctionContextTemplate<*> -> {
                    when (context) {
                        is CharSequenceStreamFuncContext<*> -> {
                            @Suppress("UNCHECKED_CAST") //
                            return function.invoke(this as CharSequenceStreamFunctionContextTemplate<I>,
                                                   context as CharSequenceStreamFuncContext<I>)
                        }
                        else -> {
                            throw IllegalArgumentException("unhandled context type: ${context?.let { it::class.qualifiedName }}")
                        }
                    }
                }
                is CharSequenceStreamOperationContextTemplate<*> -> {
                    when (context) {
                        is CharSequenceStreamOpContext<*> -> { //@Suppress("UNCHECKED_CAST") //
                            //return function.invoke(this as CharSequenceStreamOperationContextTemplate<I>,
                            //                       context as CharSequenceStreamOpContext<I>)
                            throw IllegalArgumentException("not supported for use in this function")
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
                                                                                          function: (CharSequenceStreamFunctionContextTemplate<I>, CharSequenceStreamFuncContext<I>) -> CharSequenceStreamFuncContext<I>): CTX {
            return streamContextFold<I, CTX, CTX>(context,
                                                  function.andThen { ctx ->
                                                      @Suppress("UNCHECKED_CAST") //
                                                      ctx as CTX
                                                  })
        }

    }

    override fun createConventionForStringInput(): InputSpec<String> {
        val template = CharSequenceStreamFunctionContextTemplate.getDefaultInstance<String>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    override fun <I : Any> createConventionFor(): InputSpec<I> {
        val template = CharSequenceStreamFunctionContextTemplate.getDefaultInstance<I>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    override fun <I : Any> createConventionFrom(convention: NamingConvention<I>): InputMappingSpec<I> {
        return DefaultInputMappingSpec<I>(convention)
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

        override fun followConvention(transformation: SegmentTransformationSpec.() -> Unit): DelimiterSpec<I> {
            val fullTransformationSpec = DefaultSegmentTransformationSpec<I, CTX>(charSeqOpTemplate,
                                                                                  charSeqOpContext)
            transformation.invoke(fullTransformationSpec)
            val inputTransformer: (I) -> ImmutableList<String> =
                    charSeqOpTemplate.streamContextFold<I, CTX, (I) -> ImmutableList<String>>(fullTransformationSpec.charSeqOpContext) { _, ctx ->
                        ctx.inputToCharSequenceTransformer.andThen { csStream: Stream<CharSequence> ->
                            when (ctx.streamFunction) {
                                is LeftStreamFunction -> {
                                    ctx.streamFunction.mapLeftToRight { cStream: Stream<Char> ->
                                        cStream.reduce(StringBuilder(),
                                                       StringBuilder::append,
                                                       StringBuilder::append)
                                    }
                                            .fold({ _, _ -> csStream },
                                                  { function -> function.invoke(csStream) })
                                }
                                is RightStreamFunction -> {
                                    ctx.streamFunction.fold({ _, _ -> csStream },
                                                            { function -> function.invoke(csStream) })
                                }
                            }.reduce(persistentListOf(),
                                     { pl, s -> pl.add(s.toString()) },
                                     { pl1, pl2 -> pl1.addAll(pl2) })
                        }
                    }
            return DefaultDelimiterSpec<I>(charSequenceTransformer = inputTransformer)
        }
    }

    internal class DefaultSegmentTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                            var charSeqOpContext: CTX) : SegmentTransformationSpec {

        override fun forFirstSegment(transformation: FirstSegmentTransformationSpec.() -> Unit) {
            val firstSegmentTransformationSpec = DefaultFirstSegmentTransformationSpec<I, CTX>(charSeqOpTemplate = charSeqOpTemplate,
                                                                                               charSeqOpContext = charSeqOpContext)
            transformation.invoke(firstSegmentTransformationSpec)
            charSeqOpContext = firstSegmentTransformationSpec.charSeqOpContext
        }

        override fun forLastSegment(transformation: LastSegmentTransformationSpec.() -> Unit) {
            val lastSegmentTransformationSpec = DefaultLastSegmentTransformationSpec<I, CTX>(charSeqOpTemplate = charSeqOpTemplate,
                                                                                             charSeqOpContext = charSeqOpContext)
            transformation.invoke(lastSegmentTransformationSpec)
            charSeqOpContext = lastSegmentTransformationSpec.charSeqOpContext
        }

        override fun forEverySegment(transformation: StringTransformationSpec.() -> Unit) {
            val stringTransformationSpec = DefaultStringTransformationSpec<I, CTX>(charSeqOpTemplate,
                                                                                   charSeqOpContext)
            transformation.invoke(stringTransformationSpec)
            charSeqOpContext = stringTransformationSpec.charSeqOpContext
        }

        override fun transformSegmentsByWindow(window: CharSequenceWindowRangeOpenSpec.() -> CompleteCharSequenceWindowSpec) {
            when (val completeWindowSpec: CompleteCharSequenceWindowSpec = window.invoke(DefaultCharSequenceWindowRangeOpenSpec())) {
                is DefaultCompleteCharSequenceWindowTransformationSpec -> {
                    if (completeWindowSpec.precededByEndSequenceCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                                if (csTriple.first != null && completeWindowSpec.startSequenceCondition.invoke(csTriple.second) && completeWindowSpec.precededByEndSequenceCondition.invoke(csTriple.first!!)) {
                                    listOf(completeWindowSpec.transformer.invoke(csTriple.second))
                                } else {
                                    listOf(csTriple.second)
                                }
                            }
                        }
                    } else if (completeWindowSpec.followedByEndSequenceCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                                if (csTriple.third != null && completeWindowSpec.startSequenceCondition.invoke(csTriple.second) && completeWindowSpec.followedByEndSequenceCondition.invoke(csTriple.third!!)) {
                                    listOf(completeWindowSpec.transformer.invoke(csTriple.second))
                                } else {
                                    listOf(csTriple.second)
                                }
                            }
                        }
                    } else {
                        throw IllegalArgumentException("neither a preceded_by nor a followed_by condition was supplied for the given window spec")
                    }
                }
                is DefaultCompleteCharSequenceWindowSplitSpec -> {
                    if (completeWindowSpec.precededByEndSequenceCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                                if (csTriple.first != null && completeWindowSpec.startSequenceCondition.invoke(csTriple.second) && completeWindowSpec.precededByEndSequenceCondition.invoke(csTriple.first!!)) {
                                    completeWindowSpec.transformer.invoke(csTriple.second)
                                } else {
                                    listOf(csTriple.second)
                                }
                            }
                        }
                    } else if (completeWindowSpec.followedByEndSequenceCondition != null) {
                        charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                            t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                                if (csTriple.third != null && completeWindowSpec.startSequenceCondition.invoke(csTriple.second) && completeWindowSpec.followedByEndSequenceCondition.invoke(csTriple.third!!)) {
                                    completeWindowSpec.transformer.invoke(csTriple.second)
                                } else {
                                    listOf(csTriple.second)
                                }
                            }
                        }
                    } else {
                        throw IllegalArgumentException("neither a preceded_by nor a followed_by condition was supplied for the given window spec")
                    }
                }
                else -> {
                    throw IllegalStateException("unhandled complete charsequence window spec type instance: ${completeWindowSpec::class.qualifiedName}")
                }
            }
        }

        override fun splitAnySegmentsWith(delimiter: Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.splitCharacterSequences(ctx) { cs: CharSequence ->
                    Iterable {
                        Spliterators.iterator(cs.split(delimiter)
                                                      .stream()
                                                      .filter { s: String -> s.isNotEmpty() }
                                                      .spliterator())
                    }
                }
            }
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
                             replacement: CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    cs.replace(regex,
                               replacement.toString())
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

        override fun prepend(prefix: CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    "${prefix}${cs}"
                }
            }
        }

        override fun append(suffix: CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    "${cs}${suffix}"
                }
            }
        }

        override fun transformAllCharacterSequences(transformer: (CharSequence) -> CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs ->
                    transformer.invoke(cs.toString())
                }
            }
        }


    }

    internal class DefaultFirstSegmentTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                                 var charSeqOpContext: CTX) : FirstSegmentTransformationSpec {

        override fun replaceLeadingCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                             function: (Char) -> CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithIndex(ctx) { idx: Int, cs: CharSequence ->
                    if (idx == 0 && cs.isNotEmpty() && condition.invoke(cs.first())) {
                        "${function.invoke(cs.first())}${
                            if (cs.length > 1) {
                                cs.slice(1 until cs.length)
                            } else {
                                ""
                            }
                        }"
                    } else {
                        cs
                    }
                }
            }
        }

        override fun prependToFirstSegment(prefix: CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithIndex(ctx) { idx: Int, cs: CharSequence ->
                    if (idx == 0) {
                        "${prefix}${cs}"
                    } else {
                        cs
                    }
                }
            }
        }
    }

    internal class DefaultLastSegmentTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                                var charSeqOpContext: CTX) : LastSegmentTransformationSpec {

        override fun appendToLastSegment(suffix: CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                    when {
                        csTriple.third != null -> listOf(csTriple.second)
                        else -> listOf("${csTriple.second}${suffix}")
                    }
                }
            }
        }

        override fun replaceTrailingCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                             function: (Char) -> CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                    when {
                        csTriple.third != null -> {
                            listOf(csTriple.second)
                        }
                        else -> {
                            if (csTriple.second.isNotEmpty() && condition.invoke(csTriple.second.last())) {
                                listOf("${csTriple.second.dropLast(1)}${function.invoke(csTriple.second.last())}")
                            } else {
                                listOf(csTriple.second)
                            }
                        }
                    }
                }
            }
        }

    }

    internal class DefaultLeadingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                        var charSeqOpContext: CTX) : LeadingCharactersSpec {

        override fun stripAnyLeadingCharacters(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequence(ctx) { cs: CharSequence ->
                    cs.dropWhile(condition)
                }
            }
        }

        override fun replaceLeadingCharactersOfOtherSegmentsIf(condition: (Char) -> Boolean,
                                                               function: (Char) -> CharSequence) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithIndex(ctx) { idx: Int, cs: CharSequence ->
                    if (idx != 0 && cs.isNotEmpty() && condition.invoke(cs.first())) {
                        "${function.invoke(cs.first())}${
                            if (cs.length > 1) {
                                cs.slice(1 until cs.length)
                            } else {
                                ""
                            }
                        }"
                    } else {
                        cs
                    }
                }
            }
        }

        override fun replaceEachLeadingCharacter(function: (Char) -> Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharactersWithIndex(ctx) { idx: Int, c: Char ->
                    if (idx == 0) {
                        function.invoke(c)
                                .toString()
                    } else {
                        c.toString()
                    }
                }
            }
        }

    }

    internal class DefaultTrailingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                         var charSeqOpContext: CTX) : TrailingCharactersSpec {

        override fun stripAnyTrailingCharacters(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacterSequenceWithTripleWindow(ctx) { csTriple: Triple<CharSequence?, CharSequence, CharSequence?> ->
                    when {
                        csTriple.third != null -> listOf(csTriple.second)
                        else -> listOf(csTriple.second.trimEnd(condition))
                    }
                }
            }
        }

    }

    internal class DefaultAllCharacterSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                   var charSeqOpContext: CTX) : AllCharacterSpec {

        override fun removeAny(condition: (Char) -> Boolean) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.filterCharacters(ctx,
                                   condition.negate<Char>())
            }
        }

        override fun transformAnyCharacterIf(condition: (Char) -> Boolean,
                                             transformer: (Char) -> Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacters(ctx) { c: Char ->
                    if (condition.invoke(c)) {
                        transformer.invoke(c)
                                .toString()
                    } else {
                        c.toString()
                    }
                }
            }
        }

        override fun transformAllCharacters(transformer: (Char) -> Char) {
            charSeqOpContext = charSeqOpTemplate.streamContextApply<I, CTX>(charSeqOpContext) { t, ctx ->
                t.mapCharacters(ctx,
                                transformer.andThen { c: Char -> c.toString() })
            }
        }

        override fun transformCharactersByWindow(window: CharacterWindowRangeOpenSpec.() -> CompleteCharacterWindowSpec) {
            when (val completeWindowSpec = window.invoke(DefaultCharacterWindowRangeOpenSpec())) {
                is DefaultCompleteCharacterWindowTransformationSpec -> {
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
                        throw IllegalArgumentException("no preceded_by or followed_by condition was supplied in ${CompleteCharacterWindowSpec::class.qualifiedName}")
                    }
                }
                else -> {
                    throw IllegalStateException("complete_window_spec type not supported: ${completeWindowSpec::class.qualifiedName}")
                }
            }
        }

    }

    internal class DefaultCharacterWindowRangeOpenSpec : CharacterWindowRangeOpenSpec {

        override fun anyCharacter(startCharacterCondition: (Char) -> Boolean): CharacterWindowRangeCloseSpec {
            return DefaultCharacterWindowRangeCloseSpec(startCondition = startCharacterCondition)
        }

    }

    internal class DefaultCharacterWindowRangeCloseSpec(val startCondition: (Char) -> Boolean) : CharacterWindowRangeCloseSpec {

        override fun precededBy(endCharacterCondition: (Char) -> Boolean): CharacterWindowActionSpec {
            return DefaultCharacterWindowActionSpec(startCondition = startCondition,
                                                    precededByCondition = endCharacterCondition)
        }

        override fun followedBy(endCharacterCondition: (Char) -> Boolean): CharacterWindowActionSpec {
            return DefaultCharacterWindowActionSpec(startCondition = startCondition,
                                                    followedByCondition = endCharacterCondition)
        }

    }

    internal class DefaultCharacterWindowActionSpec(val startCondition: (Char) -> Boolean,
                                                    val precededByCondition: ((Char) -> Boolean)? = null,
                                                    val followedByCondition: ((Char) -> Boolean)? = null) : CharacterWindowActionSpec {

        override fun transformInto(function: (Char) -> CharSequence): CompleteCharacterWindowSpec {
            return DefaultCompleteCharacterWindowTransformationSpec(startCondition = startCondition,
                                                                    precededByCondition = precededByCondition,
                                                                    followedByCondition = followedByCondition,
                                                                    transformer = function)
        }

    }

    internal class DefaultCompleteCharacterWindowTransformationSpec(val startCondition: (Char) -> Boolean,
                                                                    val precededByCondition: ((Char) -> Boolean)? = null,
                                                                    val followedByCondition: ((Char) -> Boolean)? = null,
                                                                    val transformer: (Char) -> CharSequence = { c -> c.toString() }) : CompleteCharacterWindowSpec {}


    internal class DefaultCharSequenceWindowRangeOpenSpec() : CharSequenceWindowRangeOpenSpec {

        override fun anySegment(startSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowRangeCloseSpec {
            return DefaultCharSequenceWindowRangeCloseSpec(startSequenceCondition = startSequenceCondition)
        }

    }

    internal class DefaultCharSequenceWindowRangeCloseSpec(val startSequenceCondition: (CharSequence) -> Boolean) : CharSequenceWindowRangeCloseSpec {

        override fun precededBy(endSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowActionSpec {
            return DefaultCharSequenceWindowActionSpec(startSequenceCondition = startSequenceCondition,
                                                       precededByEndSequenceCondition = endSequenceCondition)
        }

        override fun followedBy(endSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowActionSpec {
            return DefaultCharSequenceWindowActionSpec(startSequenceCondition = startSequenceCondition,
                                                       followedByEndSequenceCondition = endSequenceCondition)
        }
    }

    internal class DefaultCharSequenceWindowActionSpec(val startSequenceCondition: (CharSequence) -> Boolean,
                                                       val precededByEndSequenceCondition: ((CharSequence) -> Boolean)? = null,
                                                       val followedByEndSequenceCondition: ((CharSequence) -> Boolean)? = null) : CharSequenceWindowActionSpec {
        override fun transformInto(function: (CharSequence) -> CharSequence): CompleteCharSequenceWindowSpec {
            return DefaultCompleteCharSequenceWindowTransformationSpec(startSequenceCondition = startSequenceCondition,
                                                                       precededByEndSequenceCondition = precededByEndSequenceCondition,
                                                                       followedByEndSequenceCondition = followedByEndSequenceCondition,
                                                                       transformer = function)
        }

        override fun transformAndSplitIntoSeparateSegments(function: (CharSequence) -> Iterable<CharSequence>): CompleteCharSequenceWindowSpec {
            return DefaultCompleteCharSequenceWindowSplitSpec(startSequenceCondition = startSequenceCondition,
                                                              precededByEndSequenceCondition = precededByEndSequenceCondition,
                                                              followedByEndSequenceCondition = followedByEndSequenceCondition,
                                                              transformer = function)
        }
    }

    internal class DefaultCompleteCharSequenceWindowTransformationSpec(val startSequenceCondition: (CharSequence) -> Boolean,
                                                                       val precededByEndSequenceCondition: ((CharSequence) -> Boolean)? = null,
                                                                       val followedByEndSequenceCondition: ((CharSequence) -> Boolean)? = null,
                                                                       val transformer: (CharSequence) -> CharSequence = { cs: CharSequence -> cs }) : CompleteCharSequenceWindowSpec {}


    internal class DefaultCompleteCharSequenceWindowSplitSpec(val startSequenceCondition: (CharSequence) -> Boolean,
                                                              val precededByEndSequenceCondition: ((CharSequence) -> Boolean)? = null,
                                                              val followedByEndSequenceCondition: ((CharSequence) -> Boolean)? = null,
                                                              val transformer: (CharSequence) -> Iterable<CharSequence> = { cs: CharSequence -> listOf(cs) }) : CompleteCharSequenceWindowSpec {}


    internal class DefaultDelimiterSpec<I : Any>(val charSequenceTransformer: (I) -> ImmutableList<String>) : DelimiterSpec<I> {

        override fun joinSegmentsWith(delimiter: Char): ConventionSpec<I> {
            return DefaultConventionSpec<I>(charSequenceTransformer = charSequenceTransformer,
                                            delimiter = delimiter.toString())
        }

        override fun joinSegmentsWithoutDelimiter(): ConventionSpec<I> {
            return DefaultConventionSpec<I>(charSequenceTransformer = charSequenceTransformer,
                                            delimiter = "")
        }

    }

    internal class DefaultConventionSpec<I : Any>(val charSequenceTransformer: (I) -> ImmutableList<String>,
                                                  val delimiter: String) : ConventionSpec<I> {

        override fun named(conventionName: String): NamingConvention<I> {
            val inputTransformer: (I) -> ConventionalName = charSequenceTransformer.andThen { strs: ImmutableList<String> ->
                RawStringInputConventionalName(namingConventionKey = conventionName,
                                               rawStringNameSegments = strs,
                                               delimiter = delimiter)
            }
            return DefaultNamingConvention<I>(conventionName = conventionName,
                                              conventionKey = conventionName,
                                              derivationFunction = inputTransformer)
        }

        override fun namedAndIdentifiedBy(conventionName: String,
                                          conventionKey: Any): NamingConvention<I> {
            val inputTransformer: (I) -> ConventionalName = charSequenceTransformer.andThen { strs: ImmutableList<String> ->
                RawStringInputConventionalName(namingConventionKey = conventionKey,
                                               rawStringNameSegments = strs,
                                               delimiter = delimiter)
            }
            return DefaultNamingConvention<I>(conventionName = conventionName,
                                              conventionKey = conventionKey,
                                              derivationFunction = inputTransformer)
        }
    }

    internal class DefaultInputMappingSpec<I : Any>(val convention: NamingConvention<I>) : InputMappingSpec<I> {

        override fun <T : Any> mapping(function: (T) -> I): ConventionSpec<T> {
            return DefaultRepurposingConventionSpec<I, T>(convention = convention,
                                                          mapper = function)
        }

    }

    internal class DefaultRepurposingConventionSpec<I : Any, T : Any>(val convention: NamingConvention<I>,
                                                                      val mapper: (T) -> I) : ConventionSpec<T> {

        override fun named(conventionName: String): NamingConvention<T> {
            val derivationFunction = { t: T -> convention.deriveName(mapper.invoke(t)) }.andThen { cn: ConventionalName ->
                DefaultConventionalName(namingConventionKey = conventionName,
                                        nameSegments = cn.nameSegments,
                                        delimiter = cn.delimiter)
            }
            return DefaultNamingConvention<T>(conventionName = conventionName,
                                              conventionKey = conventionName,
                                              delimiter = convention.delimiter,
                                              derivationFunction = derivationFunction)
        }

        override fun namedAndIdentifiedBy(conventionName: String,
                                          conventionKey: Any): NamingConvention<T> {
            val derivationFunction = { t: T -> convention.deriveName(mapper.invoke(t)) }.andThen { cn: ConventionalName ->
                DefaultConventionalName(namingConventionKey = conventionKey,
                                        nameSegments = cn.nameSegments,
                                        delimiter = cn.delimiter)
            }
            return DefaultNamingConvention<T>(conventionName = conventionName,
                                              conventionKey = conventionKey,
                                              delimiter = convention.delimiter,
                                              derivationFunction = derivationFunction)
        }

    }

}