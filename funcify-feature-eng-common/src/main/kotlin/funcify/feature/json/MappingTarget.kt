package funcify.feature.json

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.tools.container.attempt.Try
import kotlin.reflect.KClass

interface MappingTarget {

    fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T>

    fun toJsonNode(): Try<JsonNode>

    fun toJsonString(): Try<String>

    fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode>
}
