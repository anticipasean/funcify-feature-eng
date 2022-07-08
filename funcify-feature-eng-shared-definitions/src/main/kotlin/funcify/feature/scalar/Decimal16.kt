package funcify.feature.scalar

import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object Decimal16 : GraphQLDecimalScalar {

    override val name: String = "Decimal16"

    override val description: String =
        """A java.math.BigDecimal based on logic behind 
            |IEEE 754-2019 "decimal32" format for 16-bit representations: 
            |{ precision: 3 digits (base10), rounding_mode: HALF_EVEN } 
            |(HALF_EVEN: AKA "Banker's rounding",  
            |corresponds to IEEE 754-2019 standard's "roundTiesToEven" 
            |rounding-direction attribute)""".flattenIntoOneLine()

    /**
     * Following the same logic as was done for the IEEE 754-2019 decimal32 format used in
     * [Decimal32] in [java.math.MathContext.DECIMAL32]:
     *
     * Formula for determining number of decimal (radix 10) digits that can be represented exactly
     * in exchange with a corresponding binary representation:
     *
     * ```
     * decimal_digit_precision_limit = round(log10(${radix}) * ${number_of_significand_bits_representation_with_that_radix})
     * ```
     *
     * binary32 to decimal32:
     * - log10(2) * 24 bits => round(7.22) => 7 decimal digits of precision
     *
     * binary16 to "decimal16":
     * - log10(2) * 11 bits => round(3.31) => 3 decimal digits of precision
     */
    override val mathContext: MathContext = MathContext(3, RoundingMode.HALF_EVEN)

    override val coercingFunction: Coercing<BigDecimal, BigDecimal> by lazy {
        GraphQLDecimalScalarCoercingFunctionFactory.createDecimalCoercingFunctionWithMathContext(
            mathContext
        )
    }

    override val graphQLScalarType: GraphQLScalarType by lazy {
        GraphQLScalarType.newScalar()
            .name(name)
            .description(description)
            .coercing(coercingFunction)
            .build()
    }
}
