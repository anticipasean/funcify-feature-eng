package funcify.feature.json.behavior.jackson

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NumericNode
import funcify.feature.json.data.KJsonData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonArrayData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonBooleanData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNullData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNumericData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonObjectData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonStringData
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.math.BigDecimal
import java.math.BigInteger

internal interface JacksonJsonNodeBasedKJsonTemplate<WT> {

    fun empty(): KJsonNullData<WT> {
        return JacksonJsonNodeBasedKJsonFactory.getNullNode()
    }

    fun fromString(stringValue: String): KJsonStringData<WT> {
        return KJsonStringData(JsonNodeFactory.instance.textNode(stringValue))
    }

    fun fromBoolean(booleanValue: Boolean): KJsonBooleanData<WT> {
        return KJsonBooleanData(JsonNodeFactory.instance.booleanNode(booleanValue))
    }

    fun fromNumber(numberValue: Number): KJsonNumericData<WT> {
        return KJsonNumericData(
            when (numberValue) {
                is Int -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is Float -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is Double -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is Long -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is BigInteger -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is BigDecimal -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                is Short -> {
                    JsonNodeFactory.instance.numberNode(numberValue)
                }
                else -> {
                    throw IllegalStateException(
                        """unsupported numeric value type: 
                           |[ type: ${numberValue::class.qualifiedName}, 
                           |value: $numberValue ]""".flatten()
                    )
                }
            }
                as NumericNode
        )
    }

    fun <L : List<KJsonData<WT, *>>> fromList(listValue: L): KJsonArrayData<WT> {
        return KJsonArrayData(
            listValue.fold(JsonNodeFactory.instance.arrayNode(listValue.size)) { an, n ->
                when (n) {
                    is KJsonStringData<WT> -> {
                        an.add(n.textNode)
                    }
                    is KJsonNumericData<WT> -> {
                        an.add(n.numericNode)
                    }
                    is KJsonBooleanData<WT> -> {
                        an.add(n.booleanNode)
                    }
                    is KJsonObjectData<WT> -> {
                        an.add(n.objectNode)
                    }
                    is KJsonArrayData<WT> -> {
                        an.add(n.arrayNode)
                    }
                    is KJsonNullData<WT> -> {
                        an.addNull()
                    }
                    else -> {
                        throw IllegalStateException(
                            "unsupported kjson_node type: [ type: ${n::class.qualifiedName}"
                        )
                    }
                }
            }
        )
    }

    fun <M : Map<String, KJsonData<WT, *>>> fromMap(mapValue: M): KJsonObjectData<WT>
}
