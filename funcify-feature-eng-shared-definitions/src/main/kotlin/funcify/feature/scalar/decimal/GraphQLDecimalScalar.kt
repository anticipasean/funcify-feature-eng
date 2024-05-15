package funcify.feature.scalar.decimal

import graphql.language.ScalarTypeDefinition
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * API for implementing GraphQLScalarTypes that handle decimal values with differing levels of
 * precision on account of the limitations of their sources
 *
 * @author smccarron
 * @created 2022-07-07
 */
interface GraphQLDecimalScalar {

    val name: String

    val description: String

    val mathContext: MathContext

    val maxPrecision: Int
        get() = mathContext.precision

    val roundingMode: RoundingMode
        get() = mathContext.roundingMode

    val coercingFunction: Coercing<BigDecimal, BigDecimal>

    val graphQLScalarTypeDefinition: ScalarTypeDefinition

    val graphQLScalarType: GraphQLScalarType
}
