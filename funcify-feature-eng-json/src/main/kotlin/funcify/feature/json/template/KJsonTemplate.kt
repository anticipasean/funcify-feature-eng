package funcify.feature.json.template

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NumericNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonArrayNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonBooleanNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonNullNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonNumericNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonObjectNode
import funcify.feature.json.container.JacksonJsonNodeBasedKJsonFactory.KJsonStringNode
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.math.BigDecimal
import java.math.BigInteger

internal interface KJsonTemplate<WT> {

    fun empty(): KJsonNullNode<WT> {
        return JacksonJsonNodeBasedKJsonFactory.getNullNode()
    }

    fun fromString(stringValue: String): KJsonStringNode<WT> {
        return KJsonStringNode(JsonNodeFactory.instance.textNode(stringValue))
    }

    fun fromBoolean(booleanValue: Boolean): KJsonBooleanNode<WT> {
        return KJsonBooleanNode(JsonNodeFactory.instance.booleanNode(booleanValue))
    }

    fun fromNumber(numberValue: Number): KJsonNumericNode<WT> {
        return KJsonNumericNode(
            when (numberValue) {
                is Int -> JsonNodeFactory.instance.numberNode(numberValue)
                is Float -> JsonNodeFactory.instance.numberNode(numberValue)
                is Double -> JsonNodeFactory.instance.numberNode(numberValue)
                is Long -> JsonNodeFactory.instance.numberNode(numberValue)
                is BigInteger -> JsonNodeFactory.instance.numberNode(numberValue)
                is BigDecimal -> JsonNodeFactory.instance.numberNode(numberValue)
                is Short -> JsonNodeFactory.instance.numberNode(numberValue)
                else ->
                    throw IllegalStateException(
                        """unsupported numeric value type: 
                            |[ type: ${numberValue::class.qualifiedName}, 
                            |value: $numberValue ]""".flatten()
                    )
            }
                as NumericNode
        )
    }

    fun <L : List<KJsonNode<WT, *>>> fromList(listValue: L): KJsonArrayNode<WT> {
        return KJsonArrayNode(
            listValue.fold(JsonNodeFactory.instance.arrayNode(listValue.size)) { an, n ->
                when (n) {
                    is KJsonStringNode<WT> -> an.add(n.textNode)
                    is KJsonNumericNode<WT> -> an.add(n.numericNode)
                    is KJsonBooleanNode<WT> -> an.add(n.booleanNode)
                    is KJsonObjectNode<WT> -> an.add(n.objectNode)
                    is KJsonArrayNode<WT> -> an.add(n.arrayNode)
                    is KJsonNullNode<WT> -> an.addNull()
                    else ->
                        throw IllegalStateException(
                            "unsupported kjson_node type: [ type: ${n::class.qualifiedName}"
                        )
                }
            }
        )
    }

    fun <M : Map<String, KJsonNode<WT, *>>> fromMap(mapValue: M): KJsonObjectNode<WT>
}
