package funcify.feature.json

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.reflect.KClass
import kotlin.reflect.cast
import org.springframework.core.ParameterizedTypeReference

internal object DefaultJsonMapperFactory : JsonMapperFactory {

    override fun builder(): JsonMapper.Builder {
        return DefaultJsonMapperBuilder()
    }

    internal class DefaultJsonMapperBuilder(
        private var jacksonObjectMapper: ObjectMapper? = null,
        private var jaywayJsonPathConfiguration: Configuration? = null
    ) : JsonMapper.Builder {

        override fun jacksonObjectMapper(objectMapper: ObjectMapper): JsonMapper.Builder {
            jacksonObjectMapper = objectMapper
            return this
        }

        override fun jaywayJsonPathConfiguration(configuration: Configuration): JsonMapper.Builder {
            jaywayJsonPathConfiguration = configuration
            return this
        }

        override fun build(): JsonMapper {
            return if (jacksonObjectMapper != null && jaywayJsonPathConfiguration != null) {
                DefaultJsonMapper(
                    jacksonObjectMapper = jacksonObjectMapper!!,
                    jaywayJsonPathConfiguration = jaywayJsonPathConfiguration!!
                )
            } else {
                val message: String =
                    """one of the following was not set in the builder: 
                        |[ jacksonObjectMapper: ${jacksonObjectMapper ?: "UNSET"}, 
                        |jaywayJsonPathConfiguration: ${jaywayJsonPathConfiguration ?: "UNSET"}
                        |""".flatten()
                throw IllegalArgumentException(message)
            }
        }
    }

    internal class DefaultJsonMapper(
        override val jacksonObjectMapper: ObjectMapper,
        override val jaywayJsonPathConfiguration: Configuration
    ) : JsonMapper {

        override fun <T> fromKotlinObject(objectInstance: T): MappingTarget {
            return DefaultKotlinObjectMappingTarget(
                objectInstance,
                jacksonObjectMapper,
                jaywayJsonPathConfiguration
            )
        }

        override fun fromJsonNode(jsonNode: JsonNode): MappingTarget {
            return DefaultJsonNodeMappingTarget(
                jsonNode,
                jacksonObjectMapper,
                jaywayJsonPathConfiguration
            )
        }

        override fun fromJsonString(jsonValue: String): MappingTarget {
            return DefaultJsonStringMappingTarget(
                jsonValue,
                jacksonObjectMapper,
                jaywayJsonPathConfiguration
            )
        }
    }

    internal class DefaultKotlinObjectMappingTarget<S>(
        private val sourceObjectInstance: S,
        private val jacksonObjectMapper: ObjectMapper,
        private val jaywayJsonPathConfiguration: Configuration,
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.success(sourceObjectInstance)
                .filter(
                    { s -> kClass.isInstance(s) },
                    { s ->
                        IllegalArgumentException(
                            """source_object_instance is not an instance of target_type: 
                            |[ expected: %s, actual: %s ]"""
                                .flatten()
                                .format(
                                    kClass.qualifiedName,
                                    s.toOption()
                                        .map { src -> src::class.qualifiedName }
                                        .getOrElse { "<NA>" }
                                )
                        )
                    }
                )
                .map { s -> kClass.cast(s) }
        }

        override fun <T : Any> toKotlinObject(
            parameterizedTypeReference: ParameterizedTypeReference<T>
        ): Try<T> {
            return Try.attempt {
                @Suppress("UNCHECKED_CAST") //
                sourceObjectInstance as T
            }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return Try.attempt { sourceObjectInstance }
                .mapNullable(
                    { s: S -> jacksonObjectMapper.valueToTree<JsonNode>(s) },
                    { -> jacksonObjectMapper.nullNode() }
                )
        }

        override fun toJsonString(): Try<String> {
            return toJsonNode().map { jn: JsonNode ->
                jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jn)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return toJsonNode().map { jn ->
                JsonPath.parse(jn, jaywayJsonPathConfiguration)
                    .read(jaywayJsonPath, JsonNode::class.java)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return toJsonNode().map { jn ->
                JsonPath.parse(jn, jaywayJsonPathConfiguration)
                    .read(jaywayJsonPath, JsonNode::class.java)
            }
        }
    }

    internal class DefaultJsonNodeMappingTarget(
        private val jsonNode: JsonNode,
        private val jacksonObjectMapper: ObjectMapper,
        private val jaywayJsonPathConfiguration: Configuration
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.attemptNullable(
                { jacksonObjectMapper.treeToValue(jsonNode, kClass.java) },
                { ->
                    IllegalStateException(
                        "null value returned for [ target_type: ${kClass::qualifiedName} ] from json_node source"
                    )
                }
            )
        }

        override fun <T : Any> toKotlinObject(
            parameterizedTypeReference: ParameterizedTypeReference<T>
        ): Try<T> {
            return Try.attempt {
                    TypeFactory.defaultInstance().constructType(parameterizedTypeReference.type)
                }
                .mapFailure { t ->
                    IllegalStateException(
                        "target type parameterized_type_reference could not be converted into [ ${JavaType::class.qualifiedName} ]",
                        t
                    )
                }
                .map { jt -> jacksonObjectMapper.treeToValue(jsonNode, jt) }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return Try.success(jsonNode)
        }

        override fun toJsonString(): Try<String> {
            return Try.attemptNullable(
                {
                    jacksonObjectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(jsonNode)
                },
                { ->
                    IllegalStateException(
                        "null value returned for string target type for json_node source"
                    )
                }
            )
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return Try.attemptNullable(
                {
                    JsonPath.parse(jsonNode, jaywayJsonPathConfiguration)
                        .read(jaywayJsonPath, JsonNode::class.java)
                },
                { ->
                    IllegalStateException(
                        "jayway_json_path returned null value for json_node source"
                    )
                }
            )
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return Try.attempt { jaywayJsonPath.read(jsonNode, jaywayJsonPathConfiguration) }
        }
    }

    internal class DefaultJsonStringMappingTarget(
        private val jsonValue: String,
        private val jacksonObjectMapper: ObjectMapper,
        private val jaywayJsonPathConfiguration: Configuration
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.attempt { jacksonObjectMapper.readValue(jsonValue, kClass.java) }
        }

        override fun <T : Any> toKotlinObject(
            parameterizedTypeReference: ParameterizedTypeReference<T>
        ): Try<T> {
            return Try.attempt {
                    TypeFactory.defaultInstance().constructType(parameterizedTypeReference.type)
                }
                .mapFailure { t ->
                    IllegalStateException(
                        "target type parameterized_type_reference could not be converted into [ ${JavaType::class.qualifiedName} ]",
                        t
                    )
                }
                .map { jt -> jacksonObjectMapper.readValue(jsonValue, jt) }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return Try.attemptNullable(
                { jacksonObjectMapper.readTree(jsonValue) },
                { ->
                    IllegalStateException(
                        "null json_node target returned for json_value string source"
                    )
                }
            )
        }

        override fun toJsonString(): Try<String> {
            return toJsonNode().map { jn ->
                jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jn)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return Try.attempt {
                JsonPath.parse(jsonValue, jaywayJsonPathConfiguration)
                    .read(jaywayJsonPath, JsonNode::class.java)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return Try.attempt {
                JsonPath.parse(jsonValue, jaywayJsonPathConfiguration)
                    .read(jaywayJsonPath, JsonNode::class.java)
            }
        }
    }
}
