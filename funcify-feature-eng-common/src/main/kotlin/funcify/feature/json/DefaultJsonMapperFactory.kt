package funcify.feature.json

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object DefaultJsonMapperFactory : JsonMapperFactory {

    override fun builder(): JsonMapper.Builder {
        return DefaultJsonMapperBuilder()
    }

    internal class DefaultJsonMapperBuilder(
        var jacksonObjectMapper: ObjectMapper? = null,
        var jaywayJsonPathConfiguration: Configuration? = null
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
        val sourceObjectInstance: S,
        val jacksonObjectMapper: ObjectMapper,
        val jaywayJsonPathConfiguration: Configuration,
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.attempt {
                @Suppress("UNCHECKED_CAST") //
                kClass.cast(sourceObjectInstance)
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
        val jsonNode: JsonNode,
        val jacksonObjectMapper: ObjectMapper,
        val jaywayJsonPathConfiguration: Configuration
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.attempt { jacksonObjectMapper.treeToValue(jsonNode, kClass.java) }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return Try.attempt { jsonNode }
        }

        override fun toJsonString(): Try<String> {
            return Try.attempt {
                jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return Try.attempt {
                JsonPath.parse(jsonNode, jaywayJsonPathConfiguration)
                    .read(jaywayJsonPath, JsonNode::class.java)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return Try.attempt { jaywayJsonPath.read(jsonNode, jaywayJsonPathConfiguration) }
        }
    }

    internal class DefaultJsonStringMappingTarget(
        val jsonValue: String,
        val jacksonObjectMapper: ObjectMapper,
        val jaywayJsonPathConfiguration: Configuration
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return Try.attempt { jacksonObjectMapper.readValue(jsonValue, kClass.java) }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return Try.attempt { jacksonObjectMapper.readTree(jsonValue) }
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
