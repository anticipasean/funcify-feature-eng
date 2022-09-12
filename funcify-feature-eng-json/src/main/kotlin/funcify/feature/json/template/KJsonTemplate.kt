package funcify.feature.json.template

import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonArrayNode
import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonBooleanNode
import funcify.feature.json.container.KJsonNode
import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNullNode
import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNumericNode
import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonObjectNode
import funcify.feature.json.container.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonStringNode

internal interface KJsonTemplate<WT> {

    fun empty(): KJsonNullNode<WT>

    fun fromString(stringValue: String): KJsonStringNode<WT>

    fun fromBoolean(booleanValue: Boolean): KJsonBooleanNode<WT>

    fun fromNumber(numberValue: Number): KJsonNumericNode<WT>

    fun <L : List<KJsonNode<WT, *>>> fromList(listValue: L): KJsonArrayNode<WT>

    fun <M : Map<String, KJsonNode<WT, *>>> fromMap(mapValue: M): KJsonObjectNode<WT>
}
