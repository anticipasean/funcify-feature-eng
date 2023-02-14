package funcify.feature.tools.json

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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

    private fun <T> sourceObjectInstanceNullFailure(targetTypeName: String?): Try<T> {
        return Try.failure<T>(
            IllegalStateException(
                "source_object_instance is null; cannot map to non-null instance of [ target_type: %s ]".format(
                    targetTypeName
                )
            )
        )
    }

    private fun <S> sourceObjectInstanceConversionResultNullExceptionSupplier(
        sourceObjectInstance: S,
        targetTypeName: String?
    ): () -> Throwable {
        return {
            IllegalStateException(
                "result from [ source_object_instance.type: %s ] conversion to [ target_type: %s ] is null".format(
                    sourceObjectInstance!!::class.qualifiedName,
                    targetTypeName
                )
            )
        }
    }

    private fun <S> extractionResultNullExceptionSupplier(
        sourceObjectInstance: S,
        jaywayJsonPath: String
    ): () -> Throwable {
        return {
            IllegalStateException(
                "result from [ source_object_instance.type: %s ] extraction of [ json_path: %s ] is null".format(
                    sourceObjectInstance!!::class.qualifiedName,
                    jaywayJsonPath
                )
            )
        }
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

        override fun <T> fromKotlinObject(objectInstance: T?): MappingTarget {
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
        private val sourceObjectInstance: S?,
        private val jacksonObjectMapper: ObjectMapper,
        private val jaywayJsonPathConfiguration: Configuration,
    ) : MappingTarget {

        override fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T> {
            return when (sourceObjectInstance) {
                null -> {
                    sourceObjectInstanceNullFailure<T>(kClass.qualifiedName)
                }
                else -> {
                    Try.success(sourceObjectInstance)
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
            }
        }

        override fun <T : Any> toKotlinObject(
            parameterizedTypeReference: ParameterizedTypeReference<T>
        ): Try<T> {
            return when (sourceObjectInstance) {
                null -> {
                    sourceObjectInstanceNullFailure<T>(parameterizedTypeReference.type.typeName)
                }
                else -> {
                    Try.attemptNullable(
                        {
                            @Suppress("UNCHECKED_CAST") //
                            sourceObjectInstance as T
                        },
                        sourceObjectInstanceConversionResultNullExceptionSupplier(
                            sourceObjectInstance,
                            parameterizedTypeReference.type.typeName
                        )
                    )
                }
            }
        }

        override fun toJsonNode(): Try<JsonNode> {
            return when (sourceObjectInstance) {
                null -> {
                    Try.success(JsonNodeFactory.instance.nullNode())
                }
                else -> {
                    Try.attemptNullable(
                        { jacksonObjectMapper.valueToTree<JsonNode>(sourceObjectInstance) },
                        sourceObjectInstanceConversionResultNullExceptionSupplier(
                            sourceObjectInstance,
                            JsonNode::class.qualifiedName
                        )
                    )
                }
            }
        }

        override fun toJsonString(): Try<String> {
            return toJsonNode().map { jn: JsonNode ->
                jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jn)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return toJsonNode().flatMap { jn ->
                Try.attemptNullable(
                    {
                        JsonPath.parse(jn, jaywayJsonPathConfiguration)
                            .read(jaywayJsonPath, JsonNode::class.java)
                    },
                    extractionResultNullExceptionSupplier(sourceObjectInstance, jaywayJsonPath)
                )
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return toJsonNode().flatMap { jn ->
                Try.attemptNullable(
                    {
                        JsonPath.parse(jn, jaywayJsonPathConfiguration)
                            .read(jaywayJsonPath, JsonNode::class.java)
                    },
                    extractionResultNullExceptionSupplier(sourceObjectInstance, jaywayJsonPath.path)
                )
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
                sourceObjectInstanceConversionResultNullExceptionSupplier(
                    jsonNode,
                    kClass.qualifiedName
                )
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
                sourceObjectInstanceConversionResultNullExceptionSupplier(
                    jsonNode,
                    String::class.qualifiedName
                )
            )
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return Try.attemptNullable(
                {
                    JsonPath.parse(jsonNode, jaywayJsonPathConfiguration)
                        .read(jaywayJsonPath, JsonNode::class.java)
                },
                extractionResultNullExceptionSupplier(jsonNode, jaywayJsonPath)
            )
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return Try.attemptNullable(
                { jaywayJsonPath.read(jsonNode, jaywayJsonPathConfiguration) },
                extractionResultNullExceptionSupplier(jsonNode, jaywayJsonPath.path)
            )
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
                sourceObjectInstanceConversionResultNullExceptionSupplier(
                    jsonValue,
                    JsonNode::class.qualifiedName
                )
            )
        }

        override fun toJsonString(): Try<String> {
            return toJsonNode().map { jn ->
                jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jn)
            }
        }

        override fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode> {
            return Try.attemptNullable(
                {
                    JsonPath.parse(jsonValue, jaywayJsonPathConfiguration)
                        .read(jaywayJsonPath, JsonNode::class.java)
                },
                extractionResultNullExceptionSupplier(jsonValue, jaywayJsonPath)
            )
        }

        override fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode> {
            return Try.attemptNullable(
                {
                    JsonPath.parse(jsonValue, jaywayJsonPathConfiguration)
                        .read(jaywayJsonPath, JsonNode::class.java)
                },
                extractionResultNullExceptionSupplier(jsonValue, jaywayJsonPath.path)
            )
        }
    }
}
