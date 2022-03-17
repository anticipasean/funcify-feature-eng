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

    fun fromRawString(): InputDelimiterSpec<String>

    fun <I : Any> fromInputType(inputType: KClass<I>): StringExtractionSpec<I>


    interface StringExtractionSpec<I> {
        fun extractOneOrMoreStrings(function: (I) -> Iterable<String>): InputDelimiterSpec<I>
    }

    interface InputDelimiterSpec<I> {
        fun splitWith(inputDelimiter: Char): OutputSpec<I>
    }

    interface OutputSpec<I> {
        fun andTransform(transformation: TransformationSpec.() -> Unit = {}): NamingConvention<I>
    }

    interface TransformationSpec {
        fun stripAnyLeadingOrTailingCharacters(condition: (Char) -> Boolean)
        fun replaceAnyCharacterIf(condition: (Char) -> Boolean,
                                  function: (Char) -> String)

        fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                  function: (Char) -> String)

        fun transformAny(startCharacterCondition: (Char) -> Boolean): TerminalWindowSpec
    }

    interface LeadingAndTailingCharactersTransformationSpec {
        fun stripAnyLeadingOrTailingCharacters(condition: (Char) -> Boolean)
    }

    interface AllCharacterTransformationSpec {
        fun replaceAnyCharacterIf(condition: (Char) -> Boolean,
                                  function: (Char) -> String)
    }

    interface FirstCharacterTransformationSpec {
        fun replaceFirstCharacterOfFirstSegmentIf(condition: (Char) -> Boolean,
                                                  function: (Char) -> String)
    }

    interface WindowInitializationSpec {
        fun transformAny(startCharacterCondition: (Char) -> Boolean): TerminalWindowSpec
    }

    interface TerminalWindowSpec {
        fun precededBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec
        fun followedBy(endCharacterCondition: (Char) -> Boolean): WindowActionSpec
    }

    interface WindowActionSpec {
        fun into(function: (Char) -> Char)
    }

    interface DelimiterTransformationSpec {
        fun joinWith(inputDelimiter: Char)
    }

}