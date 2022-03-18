package funcify.naming.convention

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
internal class DefaultNamingConventionFactory() : NamingConventionFactory {

    override fun createConventionForRawStrings(): InputSpec<String> {
        TODO("Not yet implemented")
    }

    override fun <I : Any> createConventionFor(inputType: KClass<I>): InputSpec<I> {
        TODO("Not yet implemented")
    }

    class DefaultInputSpec<I>() : InputSpec<I> {
        override fun whenInputProvided(extraction: StringExtractionSpec<I>.() -> StringExtractionSpec<I>): OutputSpec<I> {
            TODO("Not yet implemented")
        }
    }

    class DefaultStringExtractionSpec<I>() : StringExtractionSpec<I> {
        override fun extractOneOrMoreSegmentsWith(function: (I) -> Iterable<String>): StringExtractionSpec<I> {
            TODO("Not yet implemented")
        }

        override fun <I> splitIntoSegmentsWith(inputDelimiter: Char): StringExtractionSpec<I> {
            TODO("Not yet implemented")
        }

        override fun <I> splitIntoSegmentsWith(delimiterExpression: Regex): StringExtractionSpec<I> {
            TODO("Not yet implemented")
        }
    }

    class DefaultOutputSpec<I>() : OutputSpec<I> {
        override fun followConvention(transformation: FullTransformationSpec.() -> FullTransformationSpec): NamingConvention<I> {
            TODO("Not yet implemented")
        }
    }

    class DefaultChunkCharacterTransformationSpec() : CharacterTransformationSpec {
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

    class DefaultFullTransformationSpec() : FullTransformationSpec {
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

    class DefaultLeadingCharactersSpec() : LeadingCharactersSpec {
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

    class DefaultTrailingCharactersSpec() : TrailingCharactersSpec {
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

    class DefaultAllCharacterSpec() : AllCharacterSpec {
        override fun replaceIf(condition: (Char) -> Boolean,
                               mapper: (Char) -> String): AllCharacterSpec {
            TODO("Not yet implemented")
        }

        override fun transform(window: WindowRangeOpenSpec.() -> CompleteWindowSpec): AllCharacterSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultStringTransformationSpec() : StringTransformationSpec {
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

    class DefaultWindowRangeOpenSpec() : WindowRangeOpenSpec {
        override fun anyCharacter(startCharacterCondition: (Char) -> Boolean): WindowRangeCloseSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultWindowRangeCloseSpec : WindowRangeCloseSpec {
        override fun precededBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            TODO("Not yet implemented")
        }

        override fun followedBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultWindowActionSpec() : WindowActionSpec {
        override fun into(function: (Char) -> Char): CompleteWindowSpec {
            TODO("Not yet implemented")
        }
    }

    class DefaultCompleteWindowSpec() : CompleteWindowSpec {

    }


}