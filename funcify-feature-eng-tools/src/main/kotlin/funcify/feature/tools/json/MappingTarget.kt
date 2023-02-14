package funcify.feature.tools.json

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.JsonPath
import funcify.feature.tools.container.attempt.Try
import kotlin.reflect.KClass
import org.springframework.core.ParameterizedTypeReference

interface MappingTarget {

    companion object {

        inline fun <reified T : Any> MappingTarget.toKotlinObject(): Try<T> {
            val adHocParameterizedTypeRef: ParameterizedTypeReference<T> =
                object : ParameterizedTypeReference<T>() {}
            return this.toKotlinObject(adHocParameterizedTypeRef)
        }

    }

    fun <T : Any> toKotlinObject(kClass: KClass<T>): Try<T>

    fun <T : Any> toKotlinObject(parameterizedTypeReference: ParameterizedTypeReference<T>): Try<T>

    fun toJsonNode(): Try<JsonNode>

    fun toJsonString(): Try<String>

    fun toJsonNodeForPath(jaywayJsonPath: String): Try<JsonNode>

    fun toJsonNodeForPath(jaywayJsonPath: JsonPath): Try<JsonNode>
}
