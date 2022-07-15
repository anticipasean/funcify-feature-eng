package funcify.feature.scalar

import funcify.feature.util.StringExtensions.flatten
import graphql.language.Description
import graphql.language.ScalarTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.math.MathContext

object Decimal16 : GraphQLDecimalScalar {

    override val name: String = "Decimal16"

    override val description: String =
        """A java.math.BigDecimal in IEEE 754-2019 "decimal64" format: 
            |{ precision: 16 digits (base10), rounding_mode: HALF_EVEN } 
            |(HALF_EVEN: AKA "Banker's rounding",  
            |corresponds to IEEE 754-2019 standard's "roundTiesToEven" 
            |rounding-direction attribute)""".flatten()

    override val mathContext: MathContext = MathContext.DECIMAL64

    override val coercingFunction: Coercing<BigDecimal, BigDecimal> by lazy {
        GraphQLDecimalScalarCoercingFunctionFactory.createDecimalCoercingFunctionWithMathContext(
            mathContext
        )
    }

    override val graphQLScalarTypeDefinition: ScalarTypeDefinition by lazy {
        ScalarTypeDefinition.newScalarTypeDefinition()
            .name(name)
            .description(
                Description(
                    description,
                    SourceLocation.EMPTY,
                    description.contains(System.lineSeparator())
                )
            )
            .build()
    }

    override val graphQLScalarType: GraphQLScalarType by lazy {
        GraphQLScalarType.newScalar()
            .name(name)
            .description(description)
            .coercing(coercingFunction)
            .definition(graphQLScalarTypeDefinition)
            .build()
    }
}
