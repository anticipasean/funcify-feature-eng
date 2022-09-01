package funcify.feature.materializer.json

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.none
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.NumericNode
import funcify.feature.scalar.decimal.Decimal16
import funcify.feature.scalar.decimal.Decimal3
import funcify.feature.scalar.decimal.Decimal7
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import java.util.*

object JsonNodeToStandardValueConverter : (JsonNode, GraphQLOutputType) -> Option<Any> {

    override fun invoke(
        resultJson: JsonNode,
        expectedGraphQLOutputType: GraphQLOutputType
    ): Option<Any> {
        val graphQLType: GraphQLType = unwrapNonNullIfPresent(expectedGraphQLOutputType)
        return when (resultJson.nodeType) {
            JsonNodeType.MISSING,
            JsonNodeType.NULL -> none()
            JsonNodeType.BOOLEAN -> {
                when (graphQLType) {
                    Scalars.GraphQLBoolean -> {
                        resultJson.asBoolean(false).some()
                    }
                    Scalars.GraphQLString -> {
                        resultJson.asText("").some()
                    }
                    else -> {
                        none()
                    }
                }
            }
            JsonNodeType.NUMBER -> {
                convertNumberNodeUsingExpectedGraphQLOutputType(
                    numberNode = resultJson as NumericNode,
                    expectedGraphQLType = graphQLType
                )
            }
            JsonNodeType.BINARY -> {
                when (graphQLType) {
                    is GraphQLList -> {
                        resultJson.binaryValue().toList().some()
                    }
                    else -> {
                        resultJson.binaryValue().some()
                    }
                }
            }
            JsonNodeType.STRING -> {
                when (graphQLType) {
                    Scalars.GraphQLID -> {
                        resultJson
                            .asText("")
                            .toOption()
                            .filter { s -> s.length == 36 }
                            .flatMap { s -> Try.attempt { UUID.fromString(s) }.getSuccess() }
                            .orElse {
                                Try.attempt { resultJson.asText("").toBigInteger() }.getSuccess()
                            }
                    }
                    ExtendedScalars.UUID -> {
                        resultJson.asText("").toOption().flatMap { s ->
                            Try.attempt { UUID.fromString(s) }.getSuccess()
                        }
                    }
                    Scalars.GraphQLInt -> {
                        Try.attempt { resultJson.asText("").toBigInteger() }.getSuccess()
                    }
                    Scalars.GraphQLString -> {
                        resultJson.asText("").some()
                    }
                    else -> {
                        resultJson.asText("").some()
                    }
                }
            }
            JsonNodeType.ARRAY -> {
                resultJson
                    .toOption()
                    .filterIsInstance<ArrayNode>()
                    .map { an -> an.toList() }
                    .fold(::emptyList, ::identity)
                    .some()
            }
            JsonNodeType.OBJECT -> {
                resultJson.fields().asSequence().reduceEntriesToPersistentMap().some()
            }
            else -> {
                none()
            }
        }
    }

    private fun unwrapNonNullIfPresent(expectedGraphQLOutputType: GraphQLOutputType): GraphQLType {
        return when (expectedGraphQLOutputType) {
            is GraphQLNonNull -> {
                expectedGraphQLOutputType.wrappedType
            }
            else -> {
                expectedGraphQLOutputType
            }
        }
    }

    private fun convertNumberNodeUsingExpectedGraphQLOutputType(
        numberNode: NumericNode,
        expectedGraphQLType: GraphQLType
    ): Option<Any> {
        return when (numberNode.numberType()) {
            JsonParser.NumberType.INT -> {
                when (expectedGraphQLType) {
                    Scalars.GraphQLID -> {
                        numberNode.bigIntegerValue().some()
                    }
                    Scalars.GraphQLInt -> {
                        numberNode.asInt(0).some()
                    }
                    Scalars.GraphQLFloat -> {
                        numberNode.asInt(0).some()
                    }
                    ExtendedScalars.GraphQLBigInteger -> {
                        numberNode.bigIntegerValue().some()
                    }
                    ExtendedScalars.GraphQLBigDecimal -> {
                        numberNode.bigIntegerValue().some()
                    }
                    else -> {
                        numberNode.asInt(0).some()
                    }
                }
            }
            JsonParser.NumberType.LONG -> {
                when (expectedGraphQLType) {
                    Scalars.GraphQLID -> {
                        numberNode.bigIntegerValue().some()
                    }
                    Scalars.GraphQLInt -> {
                        numberNode.asInt(0).some()
                    }
                    Scalars.GraphQLFloat -> {
                        numberNode.asInt(0).some()
                    }
                    ExtendedScalars.GraphQLLong -> {
                        numberNode.asLong(0).some()
                    }
                    ExtendedScalars.GraphQLBigInteger -> {
                        numberNode.bigIntegerValue().some()
                    }
                    ExtendedScalars.GraphQLBigDecimal -> {
                        numberNode.bigIntegerValue().some()
                    }
                    else -> {
                        numberNode.asLong(0).some()
                    }
                }
            }
            JsonParser.NumberType.BIG_INTEGER -> {
                when (expectedGraphQLType) {
                    Scalars.GraphQLID -> {
                        numberNode.bigIntegerValue().some()
                    }
                    Scalars.GraphQLInt -> {
                        numberNode.bigIntegerValue().some()
                    }
                    ExtendedScalars.GraphQLLong -> {
                        numberNode.bigIntegerValue().some()
                    }
                    ExtendedScalars.GraphQLBigInteger -> {
                        numberNode.bigIntegerValue().some()
                    }
                    else -> {
                        numberNode.bigIntegerValue().some()
                    }
                }
            }
            JsonParser.NumberType.FLOAT -> {
                when (expectedGraphQLType) {
                    Decimal3.graphQLScalarType,
                    Decimal7.graphQLScalarType,
                    Decimal16.graphQLScalarType -> {
                        numberNode.decimalValue().some()
                    }
                    ExtendedScalars.GraphQLBigDecimal -> {
                        numberNode.decimalValue().some()
                    }
                    else -> {
                        numberNode.decimalValue().some()
                    }
                }
            }
            JsonParser.NumberType.DOUBLE -> {
                when (expectedGraphQLType) {
                    Decimal3.graphQLScalarType,
                    Decimal7.graphQLScalarType,
                    Decimal16.graphQLScalarType -> {
                        numberNode.decimalValue().some()
                    }
                    ExtendedScalars.GraphQLBigDecimal -> {
                        numberNode.decimalValue().some()
                    }
                    else -> {
                        numberNode.decimalValue().some()
                    }
                }
            }
            JsonParser.NumberType.BIG_DECIMAL -> {
                when (expectedGraphQLType) {
                    Decimal3.graphQLScalarType,
                    Decimal7.graphQLScalarType,
                    Decimal16.graphQLScalarType -> {
                        numberNode.decimalValue().some()
                    }
                    ExtendedScalars.GraphQLBigDecimal -> {
                        numberNode.decimalValue().some()
                    }
                    else -> {
                        numberNode.decimalValue().some()
                    }
                }
            }
            else -> {
                none()
            }
        }
    }
}
