package funcify.feature.schema.json

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
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
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.scalar.decimal.Decimal16
import funcify.feature.scalar.decimal.Decimal3
import funcify.feature.scalar.decimal.Decimal7
import funcify.feature.scalar.decimal.GraphQLDecimalScalar
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import java.math.BigDecimal
import java.util.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger

/**
 * Converter to mimic what GraphQL does after something is returned as a DataFetcherResult<R> (R
 * value) Implementation Notes:
 * - The most likely candidate type's serialization should be applied before less likely candidate
 *   type's
 * - given a TextNode and an expected type of ID, test for UUID compatibility before an integral ID
 *   type
 */
object JsonNodeToStandardValueConverter : (JsonNode, GraphQLOutputType) -> Option<Any> {

    private val logger: Logger = LoggerExtensions.loggerFor<JsonNodeToStandardValueConverter>()
    /*
     * Could be derived here but is always the same
     * i.e. UUID(0, 0).toString().length
     */
    private const val UUID_LENGTH: Int = 36

    // TODO: Create scalar_transformation_environment input parameter 
    override fun invoke(
        resultJson: JsonNode,
        expectedGraphQLOutputType: GraphQLOutputType
    ): Option<Any> {
        val graphQLType: GraphQLType = unwrapNonNullIfPresent(expectedGraphQLOutputType)
        return when (resultJson.nodeType) {
            JsonNodeType.MISSING,
            JsonNodeType.NULL -> {
                none()
            }
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
                convertNumericNodeUsingExpectedGraphQLOutputType(
                    numericNode = resultJson as NumericNode,
                    expectedGraphQLType = graphQLType
                )
            }
            JsonNodeType.BINARY -> {
                when (graphQLType) {
                    is GraphQLList -> {
                        resultJson.binaryValue().asSequence().toPersistentList().some()
                    }
                    else -> {
                        resultJson.binaryValue().some()
                    }
                }
            }
            JsonNodeType.STRING -> {
                convertTextNodeUsingExpectedGraphQLType(
                    textNode = resultJson as TextNode,
                    expectedGraphQLType = graphQLType
                )
            }
            JsonNodeType.ARRAY -> {
                resultJson
                    .toOption()
                    .filterIsInstance<ArrayNode>()
                    .map { an -> an.toPersistentList() }
                    .fold(::persistentListOf, ::identity)
                    .some()
            }
            JsonNodeType.OBJECT -> {
                resultJson
                    .toOption()
                    .filterIsInstance<ObjectNode>()
                    .map { on -> on.fields().asSequence() }
                    .fold(::emptySequence, ::identity)
                    .toPersistentMap()
                    .some()
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

    private fun convertNumericNodeUsingExpectedGraphQLOutputType(
        numericNode: NumericNode,
        expectedGraphQLType: GraphQLType
    ): Option<Any> {
        return if (expectedGraphQLType is GraphQLDecimalScalar) {
            serializeNumericNodeIntoDecimalScalarType(expectedGraphQLType, numericNode)
        } else {
            when (numericNode.numberType()) {
                JsonParser.NumberType.INT -> {
                    when (expectedGraphQLType) {
                        Scalars.GraphQLID -> {
                            numericNode.bigIntegerValue().some()
                        }
                        Scalars.GraphQLInt -> {
                            numericNode.asInt(0).some()
                        }
                        Scalars.GraphQLFloat -> {
                            numericNode.asInt(0).some()
                        }
                        ExtendedScalars.GraphQLBigInteger -> {
                            numericNode.bigIntegerValue().some()
                        }
                        ExtendedScalars.GraphQLBigDecimal -> {
                            numericNode.bigIntegerValue().some()
                        }
                        else -> {
                            numericNode.asInt(0).some()
                        }
                    }
                }
                JsonParser.NumberType.LONG -> {
                    when (expectedGraphQLType) {
                        Scalars.GraphQLID -> {
                            numericNode.bigIntegerValue().some()
                        }
                        Scalars.GraphQLInt -> {
                            numericNode.asInt(0).some()
                        }
                        Scalars.GraphQLFloat -> {
                            numericNode.asInt(0).some()
                        }
                        ExtendedScalars.GraphQLLong -> {
                            numericNode.asLong(0).some()
                        }
                        ExtendedScalars.GraphQLBigInteger -> {
                            numericNode.bigIntegerValue().some()
                        }
                        ExtendedScalars.GraphQLBigDecimal -> {
                            numericNode.bigIntegerValue().some()
                        }
                        else -> {
                            numericNode.asLong(0).some()
                        }
                    }
                }
                JsonParser.NumberType.BIG_INTEGER -> {
                    when (expectedGraphQLType) {
                        Scalars.GraphQLID -> {
                            numericNode.bigIntegerValue().some()
                        }
                        Scalars.GraphQLInt -> {
                            numericNode.bigIntegerValue().some()
                        }
                        ExtendedScalars.GraphQLLong -> {
                            numericNode.bigIntegerValue().some()
                        }
                        ExtendedScalars.GraphQLBigInteger -> {
                            numericNode.bigIntegerValue().some()
                        }
                        else -> {
                            numericNode.bigIntegerValue().some()
                        }
                    }
                }
                JsonParser.NumberType.FLOAT -> {
                    when (expectedGraphQLType) {
                        ExtendedScalars.GraphQLBigDecimal -> {
                            numericNode.decimalValue().some()
                        }
                        else -> {
                            numericNode.decimalValue().some()
                        }
                    }
                }
                JsonParser.NumberType.DOUBLE -> {
                    when (expectedGraphQLType) {
                        ExtendedScalars.GraphQLBigDecimal -> {
                            numericNode.decimalValue().some()
                        }
                        else -> {
                            numericNode.decimalValue().some()
                        }
                    }
                }
                JsonParser.NumberType.BIG_DECIMAL -> {
                    when (expectedGraphQLType) {
                        ExtendedScalars.GraphQLBigDecimal -> {
                            numericNode.decimalValue().some()
                        }
                        else -> {
                            numericNode.decimalValue().some()
                        }
                    }
                }
                else -> {
                    none()
                }
            }
        }
    }

    private fun serializeNumericNodeIntoDecimalScalarType(
        decimalScalarType: GraphQLType,
        numericNode: NumericNode
    ): Option<BigDecimal> {
        return try {
            when (decimalScalarType) {
                Decimal3.graphQLScalarType -> {
                    Decimal3.coercingFunction.serialize(numericNode.decimalValue())
                }
                Decimal7.graphQLScalarType -> {
                    Decimal7.coercingFunction.serialize(numericNode.decimalValue())
                }
                Decimal16.graphQLScalarType -> {
                    Decimal16.coercingFunction.serialize(numericNode.decimalValue())
                }
                else -> {
                    numericNode.decimalValue()
                }
            }.toOption()
        } catch (t: Throwable) {
            logger.warn(
                """error occurred when serializing numeric_node as 
                    |special_decimal_type: [ decimal_scalar_type: {} ] 
                    |[ error.type: {}, error.message: {} ]
                    |"""
                    .flatten(),
                decimalScalarType
                    .toOption()
                    .filterIsInstance<GraphQLScalarType>()
                    .map { gt -> gt.name }
                    .getOrElse { "<NA>" },
                t::class.simpleName,
                t.message,
                t
            )
            none()
        }
    }

    private fun serializeTextIntoDecimalScalarType(
        decimalScalarType: GraphQLType,
        text: String
    ): Option<BigDecimal> {
        return try {
            when (decimalScalarType) {
                Decimal3.graphQLScalarType -> {
                    Decimal3.coercingFunction.serialize(text)
                }
                Decimal7.graphQLScalarType -> {
                    Decimal7.coercingFunction.serialize(text)
                }
                Decimal16.graphQLScalarType -> {
                    Decimal16.coercingFunction.serialize(text)
                }
                else -> {
                    null
                }
            }.toOption()
        } catch (t: Throwable) {
            logger.warn(
                """error occurred when serializing numeric_node as 
                    |special_decimal_type: [ decimal_scalar_type: {} ] 
                    |[ error.type: {}, error.message: {} ]
                    |"""
                    .flatten(),
                decimalScalarType
                    .toOption()
                    .filterIsInstance<GraphQLScalarType>()
                    .map { gt -> gt.name }
                    .getOrElse { "<NA>" },
                t::class.simpleName,
                t.message,
                t
            )
            none()
        }
    }

    private fun convertTextNodeUsingExpectedGraphQLType(
        textNode: TextNode,
        expectedGraphQLType: GraphQLType,
    ): Option<Any> {
        return when (expectedGraphQLType) {
            Scalars.GraphQLID -> {
                textNode
                    .asText("")
                    .toOption()
                    .filter { s -> s.length == UUID_LENGTH }
                    .flatMap { s -> Try.attempt { UUID.fromString(s) }.getSuccess() }
                    .orElse { Try.attempt { textNode.asText("").toBigInteger() }.getSuccess() }
                    .orElse { textNode.asText("").toOption().filter { s -> s.isNotBlank() } }
            }
            ExtendedScalars.UUID -> {
                ExtendedScalars.UUID.coercing.serialize(textNode.asText("")).toOption()
            }
            Scalars.GraphQLInt -> {
                textNode.asText("").toBigIntegerOrNull().toOption()
            }
            Scalars.GraphQLString -> {
                textNode.asText("").some()
            }
            Scalars.GraphQLBoolean -> {
                if (textNode.asText("").isNotBlank()) {
                    Option(textNode.asText("").matches(Regex("(?i)(y)(es)?|(t)(rue)?")))
                } else {
                    none()
                }
            }
            Decimal3.graphQLScalarType,
            Decimal7.graphQLScalarType,
            Decimal16.graphQLScalarType, -> {
                serializeTextIntoDecimalScalarType(expectedGraphQLType, textNode.asText(""))
            }
            ExtendedScalars.DateTime -> {
                ExtendedScalars.DateTime.coercing.serialize(textNode.asText("")).toOption()
            }
            ExtendedScalars.Date -> {
                ExtendedScalars.Date.coercing.serialize(textNode.asText("")).toOption()
            }
            else -> {
                textNode.asText("").some()
            }
        }
    }
}
