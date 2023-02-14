package funcify.feature.tools.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.Configuration

/**
 * Composite of two JSON object mapping components: Jackson's [ObjectMapper] and Jayway's
 * [Configuration]
 *
 * Enables callers to abstract one or more layers away from the Jackson and Jayway implementations
 * and make sure the same settings are applied across the project rather than depending on ad-hoc
 * changes in a given file's use of one or both of these third-party components
 *
 * Example:
 * ```
 * val myMap = mutableMapOf("apples" to 4, "oranges" to 5)
 * val jsonNode: Try<JsonNode> = jsonMapper.fromKotlinObject(myMap).toJsonNode()
 * println(jsonMapper.fromJsonNode(jsonNode.orNull()!!).toJsonString())
 * ...
 * {
 *   "apples": 4,
 *   "oranges": 5
 * }
 * ```
 */
interface JsonMapper : MappingSource {

    val jacksonObjectMapper: ObjectMapper

    val jaywayJsonPathConfiguration: Configuration

    interface Builder {

        fun jacksonObjectMapper(objectMapper: ObjectMapper): Builder

        fun jaywayJsonPathConfiguration(configuration: Configuration): Builder

        fun build(): JsonMapper
    }
}
