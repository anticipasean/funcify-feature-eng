package funcify.feature.json.behavior

import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonArrayData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonBooleanData
import funcify.feature.json.data.KJsonData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNullData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonNumericData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonObjectData
import funcify.feature.json.data.jackson.JacksonJsonNodeBasedKJsonFactory.KJsonStringData

internal interface KJsonBehavior<WT> {

    fun empty(): KJsonNullData<WT>

    fun fromString(stringValue: String): KJsonStringData<WT>

    fun fromBoolean(booleanValue: Boolean): KJsonBooleanData<WT>

    fun fromNumber(numberValue: Number): KJsonNumericData<WT>

    fun <L : List<KJsonData<WT, *>>> fromList(listValue: L): KJsonArrayData<WT>

    fun <M : Map<String, KJsonData<WT, *>>> fromMap(mapValue: M): KJsonObjectData<WT>
}
