package funcify.naming.convention

import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
interface NamingConventionFactory {

    //    val nameConditionFactory: NameConditionFactory
    //
    //    val namingRuleFactory: NamingRuleFactory

    fun createConventionForRawStrings(): InputSpec<String>

    fun <I : Any> createConventionFor(inputType: KClass<I>): InputSpec<I>


    interface InputSpec<I> {

        /**
         *
         * @implementation: the unit return type of the receiver type function parameter
         * is necessary here (at least unless there's a cleaner way to handle the
         * issues that arise from having the type parameter in both the receiver type
         * and receiver function return type positions (in and out))
         */
        fun whenInputProvided(extraction: StringExtractionSpec<I>.() -> Unit): OutputSpec<I>

    }


    interface StringExtractionSpec<I> {

        fun extractOneOrMoreSegmentsWith(function: (I) -> Iterable<String>)

        fun treatAsOneSegment(function: (I) -> String = { i -> i.toString() })

    }

    interface OutputSpec<I> {

        fun followConvention(transformation: FullTransformationSpec.() -> FullTransformationSpec): NamingConvention<I>

    }

    interface CharacterTransformationSpec {

        fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> LeadingCharactersSpec): CharacterTransformationSpec

        fun forAnyCharacter(transformation: AllCharacterSpec.() -> AllCharacterSpec): CharacterTransformationSpec

        fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> TrailingCharactersSpec): CharacterTransformationSpec

    }

    interface StringTransformationSpec : CharacterTransformationSpec {

        fun replace(regex: Regex,
                    replacement: String): StringTransformationSpec

        fun replace(regex: Regex,
                    transform: (MatchResult) -> CharSequence): StringTransformationSpec

        fun prepend(prefix: String): StringTransformationSpec

        fun append(suffix: String): StringTransformationSpec

        fun transform(function: String.() -> String): StringTransformationSpec

    }

    interface SegmentTransformationSpec {

        fun forEachSegment(transformation: StringTransformationSpec.() -> CharacterTransformationSpec): SegmentTransformationSpec

    }

    interface FullTransformationSpec : CharacterTransformationSpec,
                                       SegmentTransformationSpec {

        fun joinSegmentsWith(inputDelimiter: Char): FullTransformationSpec

        fun joinSegmentsWithoutAnyDelimiter(): FullTransformationSpec
    }

    interface RelativePositionalTransformationSpec {

    }

    interface LeadingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAny(condition: (Char) -> Boolean): LeadingCharactersSpec

        fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                  function: (Char) -> String): LeadingCharactersSpec

        fun prependToFirstSegment(prefix: String): LeadingCharactersSpec

        fun prependSegment(segment: String): LeadingCharactersSpec

    }

    interface TrailingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAny(condition: (Char) -> Boolean): TrailingCharactersSpec

        fun replaceLastCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                function: (Char) -> String): TrailingCharactersSpec

        fun appendToLastSegment(suffix: String): TrailingCharactersSpec

        fun appendSegment(segment: String): TrailingCharactersSpec

    }

    interface AllCharacterSpec : RelativePositionalTransformationSpec {

        fun replaceIf(condition: (Char) -> Boolean,
                      mapper: (Char) -> String): AllCharacterSpec

        fun transform(window: WindowRangeOpenSpec.() -> CompleteWindowSpec): AllCharacterSpec
    }

    interface CharacterWindowSpec {

    }

    interface WindowRangeOpenSpec : CharacterWindowSpec {

        fun anyCharacter(startCharacterCondition: (Char) -> Boolean): WindowRangeCloseSpec

    }

    interface WindowRangeCloseSpec : CharacterWindowSpec {

        fun precededBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec

        fun followedBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec

    }

    interface WindowActionSpec : CharacterWindowSpec {

        fun into(function: (Char) -> Char): CompleteWindowSpec

    }

    interface CompleteWindowSpec : CharacterWindowSpec {

    }

}