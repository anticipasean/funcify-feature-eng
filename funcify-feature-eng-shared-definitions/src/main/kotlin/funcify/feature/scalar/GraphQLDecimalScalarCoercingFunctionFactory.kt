package funcify.feature.scalar

import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

object GraphQLDecimalScalarCoercingFunctionFactory {

    fun createDecimalCoercingFunctionWithMathContext(
        mathContext: MathContext
    ): Coercing<BigDecimal, BigDecimal> {
        return DecimalCoercingFunction(mathContext = mathContext)
    }

    private class DecimalCoercingFunction(val mathContext: MathContext) :
        Coercing<BigDecimal, BigDecimal> {

        companion object {

            private inline fun <reified N, reified T : Throwable> N?
                .asBigDecimalRoundedIfExceedsMaxPrecision(
                mathContext: MathContext,
                crossinline converter: (N) -> BigDecimal,
                crossinline exceptionWrapper: (String, Throwable) -> T
            ): BigDecimal {
                if (this == null) {
                    val message =
                        """[ input: null ] cannot be converted 
                           |into ${BigDecimal::class.qualifiedName}""".flattenIntoOneLine()
                    throw exceptionWrapper.invoke(message, IllegalArgumentException(message))
                }
                return try {
                    val bd = converter.invoke(this)
                    when {
                        bd.precision() > mathContext.precision -> bd.round(mathContext)
                        else -> bd
                    }
                } catch (ne: NumberFormatException) {
                    throw exceptionWrapper.invoke(
                        """input is not a valid representation of a ${BigDecimal::class.qualifiedName}""",
                        ne
                    )
                } catch (ae: ArithmeticException) {
                    throw exceptionWrapper.invoke(
                        """per documentation on ${BigDecimal::class.qualifiedName}, 
                           |[ math_context.rounding_mode: ${mathContext.roundingMode} ] 
                           |may need to be changed for input values like this one;
                           |rounding result may be inexact""".flattenIntoOneLine(),
                        ae
                    )
                }
            }
        }

        /** See contract for [Coercing.serialize] */
        override fun serialize(dataFetcherResult: Any): BigDecimal {
            return when (dataFetcherResult) {
                is BigDecimal -> {
                    dataFetcherResult.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { bd -> bd },
                        ::CoercingSerializeException
                    )
                }
                is Number -> {
                    dataFetcherResult.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { n -> BigDecimal(n.toString(), mathContext) },
                        ::CoercingSerializeException
                    )
                }
                is CharSequence -> {
                    dataFetcherResult.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { cs -> BigDecimal(cs.toString(), mathContext) },
                        ::CoercingSerializeException
                    )
                }
                is CharArray -> {
                    dataFetcherResult.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { ca -> BigDecimal(ca, mathContext) },
                        ::CoercingSerializeException
                    )
                }
                else -> {
                    throw CoercingSerializeException(
                        """expected: input of type 
                            |{ ${BigDecimal::class.qualifiedName}, 
                            |${Number::class.qualifiedName}, 
                            |${CharSequence::class.qualifiedName}, or
                            |${CharArray::class.qualifiedName} }, 
                            |actual: ${dataFetcherResult::class.qualifiedName}
                            |""".flattenIntoOneLine()
                    )
                }
            }
        }

        /** See contract for [Coercing.parseValue] */
        override fun parseValue(input: Any): BigDecimal {
            return when (input) {
                is BigDecimal -> {
                    input.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { bd -> bd },
                        ::CoercingParseValueException
                    )
                }
                is Number -> {
                    input.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { n -> BigDecimal(n.toString(), mathContext) },
                        ::CoercingParseValueException
                    )
                }
                is CharSequence -> {
                    input.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { cs -> BigDecimal(cs.toString(), mathContext) },
                        ::CoercingParseValueException
                    )
                }
                is CharArray -> {
                    input.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { ca -> BigDecimal(ca, mathContext) },
                        ::CoercingParseValueException
                    )
                }
                else -> {
                    throw CoercingParseValueException(
                        """expected: input of type 
                            |{ ${BigDecimal::class.qualifiedName}, 
                            |${Number::class.qualifiedName}, 
                            |${CharSequence::class.qualifiedName}, or 
                            |${CharArray::class.qualifiedName} }, 
                            |actual: ${input::class.qualifiedName}
                            |""".flattenIntoOneLine()
                    )
                }
            }
        }

        /** See contract for [Coercing.parseLiteral] */
        override fun parseLiteral(input: Any): BigDecimal {
            return when (input) {
                is FloatValue -> {
                    input.value.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { bd -> bd },
                        ::CoercingParseLiteralException
                    )
                }
                is StringValue -> {
                    input.value.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        { str -> BigDecimal(str, mathContext) },
                        ::CoercingParseLiteralException
                    )
                }
                is IntValue -> {
                    input.value.asBigDecimalRoundedIfExceedsMaxPrecision(
                        mathContext,
                        BigInteger::toBigDecimal,
                        ::CoercingParseLiteralException
                    )
                }
                else -> {
                    throw CoercingParseLiteralException(
                        """expected: input of type 
                            |{ ${FloatValue::class.qualifiedName}, 
                            |${StringValue::class.qualifiedName}, or 
                            |${IntValue::class.qualifiedName} } , 
                            |actual: ${input::class.qualifiedName}
                            |""".flattenIntoOneLine()
                    )
                }
            }
        }

        /**
         * Method where variables obtained from Query could be used to alter scalar value handling
         * but this current implementation (and that of most other coercing methods of
         * GraphQLScalarTypes) does not use or consider this information
         */
        override fun parseLiteral(input: Any, variables: MutableMap<String, Any>?): BigDecimal {
            return parseLiteral(input)
        }

        /** See contract for [Coercing.valueToLiteral] */
        override fun valueToLiteral(input: Any): Value<out Value<*>> {
            return FloatValue.newFloatValue(
                    when (input) {
                        is BigDecimal -> {
                            input.asBigDecimalRoundedIfExceedsMaxPrecision(
                                mathContext,
                                { bd -> bd },
                                ::CoercingParseLiteralException
                            )
                        }
                        is Number -> {
                            input.asBigDecimalRoundedIfExceedsMaxPrecision(
                                mathContext,
                                { bd -> BigDecimal(bd.toString(), mathContext) },
                                ::CoercingParseLiteralException
                            )
                        }
                        is CharSequence -> {
                            input.asBigDecimalRoundedIfExceedsMaxPrecision(
                                mathContext,
                                { cs -> BigDecimal(cs.toString(), mathContext) },
                                ::CoercingParseLiteralException
                            )
                        }
                        is CharArray -> {
                            input.asBigDecimalRoundedIfExceedsMaxPrecision(
                                mathContext,
                                { ca -> BigDecimal(ca, mathContext) },
                                ::CoercingParseLiteralException
                            )
                        }
                        else -> {
                            throw CoercingParseLiteralException(
                                """expected: input of type 
                                   |{ ${BigDecimal::class.qualifiedName}, 
                                   |${Number::class.qualifiedName}, 
                                   |${CharSequence::class.qualifiedName}, or 
                                   |${CharArray::class.qualifiedName} }, 
                                   |actual: ${input::class.qualifiedName}
                                   |""".flattenIntoOneLine()
                            )
                        }
                    }
                )
                .build()
        }
    }
}
