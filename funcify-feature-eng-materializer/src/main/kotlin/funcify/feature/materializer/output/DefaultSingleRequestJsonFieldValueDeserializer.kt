package funcify.feature.materializer.output

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
import funcify.feature.materializer.session.field.SingleRequestFieldMaterializationSession
import funcify.feature.scalar.decimal.Decimal16
import funcify.feature.scalar.decimal.Decimal3
import funcify.feature.scalar.decimal.Decimal7
import funcify.feature.scalar.decimal.GraphQLDecimalScalar
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.GraphQLContext
import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import java.math.BigDecimal
import java.util.*

/**
 * @author smccarron
 * @created 2024-04-23
 */
internal class DefaultSingleRequestJsonFieldValueDeserializer :
    SingleRequestJsonFieldValueDeserializer {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestJsonFieldValueDeserializer>()
        private const val UUID_LENGTH: Int = 36
    }

    override fun deserializeValueForFieldFromJsonInSession(
        session: SingleRequestFieldMaterializationSession,
        jsonValue: JsonNode
    ): Option<Any> {
        logger.info(
            "deserialize_value_for_field_from_json_in_session: [ session.field.name: {}, json_value.node_type: {} ]",
            session.field.name,
            jsonValue.nodeType
        )
        return deserializeUsingExpectedTypeContextAndLocale(
            jsonValue,
            session.fieldOutputType,
            session.graphQLContext,
            session.dataFetchingEnvironment.locale
        )
    }

    private fun deserializeUsingExpectedTypeContextAndLocale(
        resultJson: JsonNode,
        expectedGraphQLOutputType: GraphQLOutputType,
        graphQLContext: GraphQLContext,
        locale: Locale
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
                    expectedGraphQLType = graphQLType,
                    graphQLContext = graphQLContext,
                    locale = locale
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
                    expectedGraphQLType = graphQLType,
                    graphQLContext = graphQLContext,
                    locale = locale
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
        expectedGraphQLType: GraphQLType,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Option<Any> {
        return if (expectedGraphQLType is GraphQLDecimalScalar) {
            serializeNumericNodeIntoDecimalScalarType(
                expectedGraphQLType,
                numericNode,
                graphQLContext,
                locale
            )
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
        numericNode: NumericNode,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Option<BigDecimal> {
        return try {
            when (decimalScalarType) {
                Decimal3.graphQLScalarType -> {
                    Decimal3.coercingFunction.serialize(
                        numericNode.decimalValue(),
                        graphQLContext,
                        locale
                    )
                }
                Decimal7.graphQLScalarType -> {
                    Decimal7.coercingFunction.serialize(
                        numericNode.decimalValue(),
                        graphQLContext,
                        locale
                    )
                }
                Decimal16.graphQLScalarType -> {
                    Decimal16.coercingFunction.serialize(
                        numericNode.decimalValue(),
                        graphQLContext,
                        locale
                    )
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
        text: String,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Option<BigDecimal> {
        return try {
            when (decimalScalarType) {
                Decimal3.graphQLScalarType -> {
                    Decimal3.coercingFunction.serialize(text, graphQLContext, locale)
                }
                Decimal7.graphQLScalarType -> {
                    Decimal7.coercingFunction.serialize(text, graphQLContext, locale)
                }
                Decimal16.graphQLScalarType -> {
                    Decimal16.coercingFunction.serialize(text, graphQLContext, locale)
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
        graphQLContext: GraphQLContext,
        locale: Locale,
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
                ExtendedScalars.UUID.coercing
                    .serialize(textNode.asText(""), graphQLContext, locale)
                    .toOption()
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
                serializeTextIntoDecimalScalarType(
                    decimalScalarType = expectedGraphQLType,
                    text = textNode.asText(""),
                    graphQLContext = graphQLContext,
                    locale = locale
                )
            }
            ExtendedScalars.DateTime -> {
                ExtendedScalars.DateTime.coercing
                    .serialize(textNode.asText(""), graphQLContext, locale)
                    .toOption()
            }
            ExtendedScalars.Date -> {
                ExtendedScalars.Date.coercing
                    .serialize(textNode.asText(""), graphQLContext, locale)
                    .toOption()
            }
            else -> {
                textNode.asText("").some()
            }
        }
    }
}
