package funcify.feature.tools.json

import com.fasterxml.jackson.databind.JsonNode

interface MappingSource {

    fun <T> fromKotlinObject(objectInstance: T?): MappingTarget

    fun fromJsonNode(jsonNode: JsonNode): MappingTarget

    fun fromJsonString(jsonValue: String): MappingTarget
}
