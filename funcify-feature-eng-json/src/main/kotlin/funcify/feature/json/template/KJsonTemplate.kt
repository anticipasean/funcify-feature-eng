package funcify.feature.json.template

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NumericNode
import funcify.feature.json.container.KJsonNodeFactory
import funcify.feature.json.container.KJsonNodeFactory.KJsonArrayNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonBooleanNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonNullNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonNumericNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonObjectNode
import funcify.feature.json.container.KJsonNodeFactory.KJsonStringNode
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import java.math.BigDecimal
import java.math.BigInteger

internal interface KJsonTemplate<WT> {

    fun empty(): KJsonNullNode<WT> {
        return KJsonNodeFactory.getNullNode()
    }

    fun fromString(stringValue: String): KJsonStringNode<WT> {
        return KJsonStringNode(JsonNodeFactory.instance.textNode(stringValue))
    }

    fun fromBoolean(booleanValue: Boolean): KJsonBooleanNode<WT> {
        return KJsonBooleanNode(JsonNodeFactory.instance.booleanNode(booleanValue))
    }

    fun fromNumeric(numericValue: Number): KJsonNumericNode<WT> {
        return KJsonNumericNode(
            when (numericValue) {
                is Int -> JsonNodeFactory.instance.numberNode(numericValue)
                is Float -> JsonNodeFactory.instance.numberNode(numericValue)
                is Double -> JsonNodeFactory.instance.numberNode(numericValue)
                is Long -> JsonNodeFactory.instance.numberNode(numericValue)
                is BigInteger -> JsonNodeFactory.instance.numberNode(numericValue)
                is BigDecimal -> JsonNodeFactory.instance.numberNode(numericValue)
                is Short -> JsonNodeFactory.instance.numberNode(numericValue)
                else ->
                    throw IllegalStateException(
                        """unsupported numeric value type: 
                            |[ type: ${numericValue::class.qualifiedName}, 
                            |value: $numericValue ]""".flattenIntoOneLine()
                    )
            } as
                NumericNode
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
