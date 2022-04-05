package funcify.feature.naming

import funcify.feature.naming.convention.DefaultNamingConventionFactory


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
interface NamingConventionFactory {

    companion object {

        /**
         *
         * @implementation_note: Consider moving this to a factories singleton type e.g. NamingConventionFactories if
         * more than one base factory implementation is to be provided in this module
         */
        fun getDefaultFactory(): NamingConventionFactory {
            return DefaultNamingConventionFactory.getInstance()
        }

    }

    fun createConventionForStringInput(): InputSpec<String>

    fun <I : Any> createConventionFor(): InputSpec<I>

    fun <I : Any> createConventionFrom(convention: NamingConvention<I>): InputMappingSpec<I>

    interface InputSpec<I : Any> {

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

    interface OutputSpec<I : Any> {

        fun followConvention(transformation: SegmentTransformationSpec.() -> Unit): DelimiterSpec<I>

    }

    interface InputMappingSpec<I : Any> {

        fun <T : Any> mapping(function: (T) -> I): ConventionSpec<T>

    }

    interface DelimiterSpec<I : Any> {

        fun joinSegmentsWith(delimiter: Char): ConventionSpec<I>

        fun joinSegmentsWithoutDelimiter(): ConventionSpec<I>

    }

    interface ConventionSpec<I : Any> {

        fun named(conventionName: String): NamingConvention<I>

        fun namedAndIdentifiedBy(conventionName: String,
                                 conventionKey: Any): NamingConvention<I>

    }

    interface CharacterTransformationSpec {

        fun forLeadingCharacters(transformation: LeadingCharactersSpec.() -> Unit)

        fun forAnyCharacter(transformation: AllCharacterSpec.() -> Unit)

        fun forTrailingCharacters(transformation: TrailingCharactersSpec.() -> Unit)

    }

    interface StringTransformationSpec : CharacterTransformationSpec {

        fun replace(regex: Regex,
                    replacement: CharSequence)

        fun replace(regex: Regex,
                    transform: (MatchResult) -> CharSequence)

        fun prepend(prefix: CharSequence)

        fun append(suffix: CharSequence)

        fun transformAllCharacterSequences(transformer: (CharSequence) -> CharSequence)

    }

    interface SegmentTransformationSpec {

        fun forFirstSegment(transformation: FirstSegmentTransformationSpec.() -> Unit)

        fun forLastSegment(transformation: LastSegmentTransformationSpec.() -> Unit)

        fun forEverySegment(transformation: StringTransformationSpec.() -> Unit)

        fun transformSegmentsByWindow(window: CharSequenceWindowRangeOpenSpec.() -> CompleteCharSequenceWindowSpec)

        fun splitAnySegmentsWith(delimiter: Char)

    }

    interface FirstSegmentTransformationSpec : RelativePositionalTransformationSpec {

        fun replaceLeadingCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                    function: (Char) -> CharSequence)

        fun makeLeadingCharacterOfFirstSegmentUppercase() {
            return replaceLeadingCharacterOfFirstSegmentIf({ c: Char -> c.isLowerCase() },
                                                           { c: Char -> c.uppercase() })
        }

        fun makeLeadingCharacterOfFirstSegmentLowercase() {
            return replaceLeadingCharacterOfFirstSegmentIf({ c: Char -> c.isUpperCase() },
                                                           { c: Char -> c.lowercase() })
        }

        fun prependToFirstSegment(prefix: CharSequence)

    }

    interface LastSegmentTransformationSpec : RelativePositionalTransformationSpec {

        fun replaceTrailingCharacterOfLastSegmentIf(condition: (Char) -> Boolean,
                                                    function: (Char) -> CharSequence)

        fun appendToLastSegment(suffix: CharSequence)

    }

    interface RelativePositionalTransformationSpec {

    }

    interface LeadingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAnyLeadingCharacters(condition: (Char) -> Boolean)

        fun stripAnyLeadingWhitespace() {
            return stripAnyLeadingCharacters { c: Char -> c.isWhitespace() }
        }

        fun replaceLeadingCharactersOfOtherSegmentsIf(condition: (Char) -> Boolean,
                                                      function: (Char) -> CharSequence)

        fun replaceEachLeadingCharacter(function: (Char) -> Char)

        fun capitalizeEachLeadingCharacter() {
            return replaceEachLeadingCharacter { c: Char -> c.uppercaseChar() }
        }

        fun makeEachLeadingCharacterUppercase() {
            return replaceEachLeadingCharacter { c: Char -> c.uppercaseChar() }
        }

        fun makeEachLeadingCharacterLowercase() {
            return replaceEachLeadingCharacter { c: Char -> c.lowercaseChar() }
        }
    }

    interface TrailingCharactersSpec : RelativePositionalTransformationSpec {

        fun stripAnyTrailingCharacters(condition: (Char) -> Boolean)

        fun stripAnyTrailingWhitespace() {
            return stripAnyTrailingCharacters { c: Char -> c.isWhitespace() }
        }

    }

    interface AllCharacterSpec : RelativePositionalTransformationSpec {

        fun removeAny(condition: (Char) -> Boolean)

        fun removeAnyWhitespace() {
            return removeAny { c: Char -> c.isWhitespace() }
        }

        fun transformAnyCharacterIf(condition: (Char) -> Boolean,
                                    transformer: (Char) -> Char)

        fun transformAllCharacters(transformer: (Char) -> Char)

        fun makeAllUppercase() {
            return transformAllCharacters { c: Char -> c.uppercaseChar() }
        }

        fun makeAllLowercase() {
            return transformAllCharacters { c: Char -> c.lowercaseChar() }
        }

        fun transformCharactersByWindow(window: CharacterWindowRangeOpenSpec.() -> CompleteCharacterWindowSpec)
    }

    interface CharacterWindowSpec {

    }

    interface CharacterWindowRangeOpenSpec : CharacterWindowSpec {

        fun anyCharacter(startCharacterCondition: (Char) -> Boolean): CharacterWindowRangeCloseSpec

        fun anyUppercaseCharacter(): CharacterWindowRangeCloseSpec {
            return anyCharacter { c: Char -> c.isUpperCase() }
        }

        fun anyLowercaseCharacter(): CharacterWindowRangeCloseSpec {
            return anyCharacter { c: Char -> c.isLowerCase() }
        }

        fun anyDigit(): CharacterWindowRangeCloseSpec {
            return anyCharacter { c: Char -> c.isDigit() }
        }

        fun anyLetter(): CharacterWindowRangeCloseSpec {
            return anyCharacter { c: Char -> c.isLetter() }
        }

    }

    interface CharacterWindowRangeCloseSpec : CharacterWindowSpec {

        fun precededBy(endCharacterCondition: (Char) -> Boolean): CharacterWindowActionSpec

        fun precededByAnUppercaseLetter(): CharacterWindowActionSpec {
            return precededBy { c: Char -> c.isUpperCase() }
        }

        fun precededByALowercaseLetter(): CharacterWindowActionSpec {
            return precededBy { c: Char -> c.isLowerCase() }
        }

        fun precededByADigit(): CharacterWindowActionSpec {
            return precededBy { c: Char -> c.isDigit() }
        }

        fun precededByALetter(): CharacterWindowActionSpec {
            return precededBy { c: Char -> c.isLetter() }
        }

        fun followedBy(endCharacterCondition: (Char) -> Boolean): CharacterWindowActionSpec

        fun followedByAnUppercaseLetter(): CharacterWindowActionSpec {
            return followedBy { c: Char -> c.isUpperCase() }
        }

        fun followedByALowercaseLetter(): CharacterWindowActionSpec {
            return followedBy { c: Char -> c.isLowerCase() }
        }

        fun followedByADigit(): CharacterWindowActionSpec {
            return followedBy { c: Char -> c.isDigit() }
        }

        fun followedByALetter(): CharacterWindowActionSpec {
            return followedBy { c: Char -> c.isLetter() }
        }

    }

    interface CharacterWindowActionSpec : CharacterWindowSpec {

        fun transformInto(function: (Char) -> CharSequence): CompleteCharacterWindowSpec

    }

    interface CompleteCharacterWindowSpec : CharacterWindowSpec {

    }

    interface CharSequenceWindowSpec {

    }

    interface CharSequenceWindowRangeOpenSpec : CharSequenceWindowSpec {

        fun anySegment(startSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowRangeCloseSpec

        fun anyEmptySegment(): CharSequenceWindowRangeCloseSpec {
            return anySegment { cs: CharSequence -> cs.isEmpty() }
        }
    }

    interface CharSequenceWindowRangeCloseSpec : CharSequenceWindowSpec {

        fun precededBy(endSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowActionSpec

        fun followedBy(endSequenceCondition: (CharSequence) -> Boolean): CharSequenceWindowActionSpec

    }

    interface CharSequenceWindowActionSpec : CharSequenceWindowSpec {

        fun transformInto(function: (CharSequence) -> CharSequence): CompleteCharSequenceWindowSpec

        fun transformAndSplitIntoSeparateSegments(function: (CharSequence) -> Iterable<CharSequence>): CompleteCharSequenceWindowSpec

    }

    interface CompleteCharSequenceWindowSpec : CharSequenceWindowSpec {

    }

}