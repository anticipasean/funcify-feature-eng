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

        fun followConvention(transformation: FullTransformationSpec.() -> Unit): NamingConvention<I>

    }

    interface CharacterTransformationSpec {

        fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> Unit)

        fun forAnyCharacter(transformation: AllCharacterSpec.() -> Unit)

        fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> Unit)

    }

    interface StringTransformationSpec : CharacterTransformationSpec {

        fun replace(regex: Regex,
                    replacement: String)

        fun replace(regex: Regex,
                    transform: (MatchResult) -> CharSequence)

        fun prepend(prefix: String)

        fun append(suffix: String)

        fun transform(function: (String) -> String)

    }

    interface SegmentTransformationSpec {

        fun forEachSegment(transformation: StringTransformationSpec.() -> Unit)

    }

    interface FullTransformationSpec : CharacterTransformationSpec,
                                       SegmentTransformationSpec {

        fun joinSegmentsWith(inputDelimiter: Char)

        fun joinSegmentsWithoutAnyDelimiter()
    }

    interface RelativePositionalTransformationSpec {

    }

    interface LeadingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAny(condition: (Char) -> Boolean)

        fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                  function: (Char) -> String)

        fun prependToFirstSegment(prefix: String)

        fun prependSegment(segment: String)

    }

    interface TrailingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAny(condition: (Char) -> Boolean)

        fun replaceLastCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                function: (Char) -> String)

        fun appendToLastSegment(suffix: String)

        fun appendSegment(segment: String)

    }

    interface AllCharacterSpec : RelativePositionalTransformationSpec {

        fun replaceIf(condition: (Char) -> Boolean,
                      mapper: (Char) -> String)

        fun transform(window: WindowRangeOpenSpec.() -> CompleteWindowSpec)
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