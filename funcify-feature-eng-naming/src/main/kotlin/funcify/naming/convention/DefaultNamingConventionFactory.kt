package funcify.naming.convention

import funcify.naming.charseq.operation.CharSequenceStreamContext
import funcify.naming.charseq.template.CharSequenceOperationContextTemplate
import funcify.naming.charseq.template.CharSequenceStreamContextTemplate
import funcify.naming.convention.NamingConventionFactory.AllCharacterSpec
import funcify.naming.convention.NamingConventionFactory.CharacterTransformationSpec
import funcify.naming.convention.NamingConventionFactory.CompleteWindowSpec
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
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
internal class DefaultNamingConventionFactory<CTX>(val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : NamingConventionFactory {

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
                            throw IllegalArgumentException("unhandled context type: ${context?.let { javaClass.name }}")
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("unhandled template type: ${this::class.java.name}")
                }
            }
        }

    }

    override fun createConventionForRawStrings(): InputSpec<String> {
        return DefaultInputSpec<String, CTX>(charSeqOpTemplate.emptyContext(),
                                             charSeqOpTemplate)
    }

    override fun <I : Any> createConventionFor(inputType: KClass<I>): InputSpec<I> {
        return DefaultInputSpec<I, CTX>(charSeqOpTemplate.emptyContext(),
                                        charSeqOpTemplate)
    }

    class DefaultInputSpec<I, CTX>(val charSeqOpContext: CTX,
                                   val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : InputSpec<I> {

        override fun whenInputProvided(extraction: StringExtractionSpec<I>.() -> Unit): OutputSpec<I> {
            val stringExtractionSpec = DefaultStringExtractionSpec<I, CTX>(charSeqOpContext,
                                                                           charSeqOpTemplate)
            extraction.invoke(stringExtractionSpec)
            return DefaultOutputSpec<I, CTX>(stringExtractionSpec.charSeqOpContext,
                                             charSeqOpTemplate)
        }
    }

    class DefaultStringExtractionSpec<I, CTX>(var charSeqOpContext: CTX,
                                              val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : StringExtractionSpec<I> {

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

    class DefaultOutputSpec<I, CTX>(val charSeqOpContext: CTX,
                                    val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : OutputSpec<I> {

        override fun followConvention(transformation: FullTransformationSpec.() -> FullTransformationSpec): NamingConvention<I> {
            val fullTransformationSpec = DefaultFullTransformationSpec<CTX>(charSeqOpContext,
                                                                            charSeqOpTemplate)
            TODO("Not yet implemented")
        }
    }

    class DefaultChunkCharacterTransformationSpec<CTX>(val charSeqOpContext: CTX,
                                                       val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : CharacterTransformationSpec {
        override fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> LeadingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forAnyCharacter(transformation: AllCharacterSpec.() -> AllCharacterSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> TrailingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultFullTransformationSpec<CTX>(val charSeqOpContext: CTX,
                                             val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : FullTransformationSpec {

        override fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> LeadingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forAnyCharacter(transformation: AllCharacterSpec.() -> AllCharacterSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> TrailingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forEachSegment(transformation: StringTransformationSpec.() -> CharacterTransformationSpec): NamingConventionFactory.SegmentTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun joinSegmentsWith(inputDelimiter: Char): FullTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun joinSegmentsWithoutAnyDelimiter(): FullTransformationSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultLeadingCharactersSpec<CTX>(val charSeqOpContext: CTX,
                                            val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : LeadingCharactersSpec {
        override fun stripAny(condition: (Char) -> Boolean): LeadingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                           function: (Char) -> String): LeadingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun prependToFirstSegment(prefix: String): LeadingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun prependSegment(segment: String): LeadingCharactersSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultTrailingCharactersSpec<CTX>(val charSeqOpContext: CTX,
                                             val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : TrailingCharactersSpec {
        override fun stripAny(condition: (Char) -> Boolean): TrailingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun replaceLastCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                         function: (Char) -> String): TrailingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun appendToLastSegment(suffix: String): TrailingCharactersSpec {
            TODO("Not yet implemented")
        }

        override fun appendSegment(segment: String): TrailingCharactersSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultAllCharacterSpec<CTX>(val charSeqOpContext: CTX,
                                       val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : AllCharacterSpec {
        override fun replaceIf(condition: (Char) -> Boolean,
                               mapper: (Char) -> String): AllCharacterSpec {
            TODO("Not yet implemented")
        }

        override fun transform(window: WindowRangeOpenSpec.() -> CompleteWindowSpec): AllCharacterSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultStringTransformationSpec<CTX>(val charSeqOpContext: CTX,
                                               val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : StringTransformationSpec {
        override fun replace(regex: Regex,
                             replacement: String): StringTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun replace(regex: Regex,
                             transform: (MatchResult) -> CharSequence): StringTransformationSpec {
            TODO("Not yet implemented")
        }


        override fun prepend(prefix: String): StringTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun append(suffix: String): StringTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun transform(function: (String) -> String): StringTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> LeadingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forAnyCharacter(transformation: AllCharacterSpec.() -> AllCharacterSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

        override fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> TrailingCharactersSpec): CharacterTransformationSpec {
            TODO("Not yet implemented")
        }

    }

    class DefaultWindowRangeOpenSpec<CTX>(val charSeqOpContext: CTX,
                                          val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : WindowRangeOpenSpec {
        override fun anyCharacter(startCharacterCondition: (Char) -> Boolean): WindowRangeCloseSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultWindowRangeCloseSpec<CTX>(val charSeqOpContext: CTX,
                                           val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : WindowRangeCloseSpec {
        override fun precededBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            TODO("Not yet implemented")
        }

        override fun followedBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultWindowActionSpec<CTX>(val charSeqOpContext: CTX,
                                       val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : WindowActionSpec {
        override fun into(function: (Char) -> Char): CompleteWindowSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultCompleteWindowSpec<CTX>(val charSeqOpContext: CTX,
                                         val charSeqOpTemplate: CharSequenceOperationContextTemplate<CTX>) : CompleteWindowSpec {

    }


}