package funcify.feature.scalar

import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.math.MathContext

object Decimal32 : GraphQLDecimalScalar {

    override val name: String = "Decimal32"

    override val description: String =
        """A java.math.BigDecimal in IEEE 754-2019 "decimal32" format: 
            |{ precision: 7 digits (base10), rounding_mode: HALF_EVEN } 
            |(HALF_EVEN: AKA "Banker's rounding",  
            |corresponds to IEEE 754-2019 standard's "roundTiesToEven" 
            |rounding-direction attribute)""".flattenIntoOneLine()

    override val mathContext: MathContext = MathContext.DECIMAL32

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
