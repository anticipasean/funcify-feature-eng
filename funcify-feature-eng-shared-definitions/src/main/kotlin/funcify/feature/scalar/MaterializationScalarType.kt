package funcify.feature.scalar

import com.google.common.collect.ImmutableSet
import funcify.feature.scalar.decimal.Decimal16
import funcify.feature.scalar.decimal.Decimal3
import funcify.feature.scalar.decimal.Decimal7
import graphql.Scalars
import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.util.*

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
enum class MaterializationScalarType(
    val graphQLScalarType: GraphQLScalarType,
    val defaultGraphQLValue: Value<*>
) {
    ID(Scalars.GraphQLID, StringValue.of("")),
    UUID(ExtendedScalars.UUID, StringValue.of(UUID(0, 0).toString())),
    BOOLEAN(Scalars.GraphQLBoolean, BooleanValue.of(false)),
    INT(Scalars.GraphQLInt, IntValue.of(0)),
    FLOAT(Scalars.GraphQLFloat, FloatValue.newFloatValue(BigDecimal.ZERO).build()),
    STRING(Scalars.GraphQLString, StringValue.of("")),
    DECIMAL3(Decimal3.graphQLScalarType, FloatValue.newFloatValue(BigDecimal.ZERO).build()),
    DECIMAL7(Decimal7.graphQLScalarType, FloatValue.newFloatValue(BigDecimal.ZERO).build()),
    DECIMAL16(Decimal16.graphQLScalarType, FloatValue.newFloatValue(BigDecimal.ZERO).build()),
    BIGDECIMAL(ExtendedScalars.GraphQLBigDecimal, FloatValue.newFloatValue(BigDecimal.ZERO).build()),
    BIGINTEGER(ExtendedScalars.GraphQLBigInteger, IntValue.of(0)),
    BYTE(ExtendedScalars.GraphQLByte, IntValue.of(0)),
    CHAR(ExtendedScalars.GraphQLChar, StringValue.of(" ")),
    LONG(ExtendedScalars.GraphQLLong, IntValue.of(0)),
    DATE(ExtendedScalars.Date, NullValue.of()),
    DATETIME(ExtendedScalars.DateTime, NullValue.of());

    companion object {
        private val numericScalarTypes: ImmutableSet<MaterializationScalarType> by lazy {
            sequenceOf(INT, FLOAT, DECIMAL3, DECIMAL7, DECIMAL16, BIGDECIMAL, BIGINTEGER, LONG)
                .fold(ImmutableSet.builder<MaterializationScalarType>()) { sb, nst -> sb.add(nst) }
                .build()
        }
        private val temporalScalarTypes: ImmutableSet<MaterializationScalarType> by lazy {
            sequenceOf(DATE, DATETIME)
                .fold(ImmutableSet.builder<MaterializationScalarType>()) { sb, tst -> sb.add(tst) }
                .build()
        }
    }
}
