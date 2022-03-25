package funcify.naming.convention

import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.template.CharSequenceOperationContextTemplate
import funcify.naming.charseq.template.CharSequenceStreamContextTemplate
import funcify.naming.convention.NamingConventionFactory.AllCharacterSpec
import funcify.naming.convention.NamingConventionFactory.FullTransformationSpec
import funcify.naming.convention.NamingConventionFactory.InputSpec
import funcify.naming.convention.NamingConventionFactory.LeadingCharactersSpec
import funcify.naming.convention.NamingConventionFactory.OutputSpec
import funcify.naming.convention.NamingConventionFactory.StringExtractionSpec
import funcify.naming.convention.NamingConventionFactory.StringTransformationSpec
import funcify.naming.convention.NamingConventionFactory.TrailingCharactersSpec
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
internal class DefaultNamingConventionFactory() : NamingConventionFactory {

    companion object {

        private fun <I, CTX> CharSequenceOperationContextTemplate<CTX>.streamContextApply(context: CTX,
                                                                                          function: (CharSequenceStreamContextTemplate<I>, CharSequenceStreamContext<I>) -> CharSequenceStreamContext<I>): CTX {
            when (this) {
                is CharSequenceStreamContextTemplate<*> -> {
                    when (context) {
                        is CharSequenceStreamContext<*> -> {
                            @Suppress("UNCHECKED_CAST") //
                            return function.invoke(this as CharSequenceStreamContextTemplate<I>,
                                                   context as CharSequenceStreamContext<I>) as CTX
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

        private fun <T : Any?> ((T) -> Boolean).negate(): (T) -> Boolean {
            return { t ->
                !this.invoke(t)
            }
        }

    }

    override fun createConventionForRawStrings(): InputSpec<String> {
        val template = CharSequenceStreamContextTemplate.getDefaultInstance<String>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    override fun <I : Any> createConventionFor(inputType: KClass<I>): InputSpec<I> {
        val template = CharSequenceStreamContextTemplate.getDefaultInstance<I>()
        return DefaultInputSpec(template,
                                template.emptyContext())
    }

    class DefaultInputSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                   val charSeqOpContext: CTX) : InputSpec<I> {

        override fun whenInputProvided(extraction: StringExtractionSpec<I>.() -> Unit): OutputSpec<I> {
            val stringExtractionSpec = DefaultStringExtractionSpec<I, CTX>(charSeqOpTemplate,
                                                                           charSeqOpContext)
            extraction.invoke(stringExtractionSpec)
            return DefaultOutputSpec<I, CTX>(charSeqOpTemplate,
                                             stringExtractionSpec.charSeqOpContext)
        }
    }

    class DefaultStringExtractionSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
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

    class DefaultOutputSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                    val charSeqOpContext: CTX) : OutputSpec<I> {

        override fun followConvention(transformation: FullTransformationSpec.() -> Unit): NamingConvention<I> {
            TODO("Not yet implemented")
        }


    }

    class DefaultFullTransformationSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
                                                var charSeqOpContext: CTX) : FullTransformationSpec {

        override fun joinSegmentsWith(inputDelimiter: Char) {
            TODO("Not yet implemented")
        }

        override fun joinSegmentsWithoutAnyDelimiter() {
            TODO("Not yet implemented")
        }

        override fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> Unit) {
            val leadingCharactersSpec = DefaultLeadingCharactersSpec<I, CTX>(charSeqOpTemplate,
                                                                             charSeqOpContext)
            transformation.invoke(leadingCharactersSpec)
            charSeqOpContext = leadingCharactersSpec.charSeqOpContext
        }

        override fun forAnyCharacter(transformation: AllCharacterSpec.() -> Unit) {
            TODO("Not yet implemented")
        }

        override fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> Unit) {
            val trailingCharactersSpec = DefaultTrailingCharactersSpec<I, CTX>(charSeqOpTemplate,
                                                                               charSeqOpContext)
            transformation.invoke(trailingCharactersSpec)
            charSeqOpContext = trailingCharactersSpec.charSeqOpContext
        }

        override fun forEachSegment(transformation: StringTransformationSpec.() -> Unit) {
            TODO("Not yet implemented")
        }

    }

    class DefaultLeadingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
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

    class DefaultTrailingCharactersSpec<I, CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>,
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
                        (if (cs.length > 1) {
                            cs.slice(0 until cs.length - 1)
                        } else {
                            ""
                        }) as String + function.invoke(cs.last())
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

}