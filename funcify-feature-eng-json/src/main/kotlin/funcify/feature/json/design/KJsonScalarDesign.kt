package funcify.feature.json.design

import funcify.feature.json.KJson
import funcify.feature.json.KJsonContainer
import funcify.feature.json.KJsonScalar
import funcify.feature.json.behavior.KJsonScalarBehavior
import funcify.feature.json.data.KJsonData
import funcify.feature.json.data.KJsonScalarData

internal interface KJsonScalarDesign<WT, I> : KJsonDesign<WT, I>, KJsonScalar {

    override val behavior: KJsonScalarBehavior<WT>

    override val data: KJsonScalarData<WT, I>

    override fun isString(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isNumber(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isBoolean(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isNull(): Boolean {
        TODO("Not yet implemented")
    }

    override fun mapString(mapper: (String) -> String): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapStringToObject(mapper: (String) -> Pair<String, String>): KJson {
        TODO("Not yet implemented")
    }

    override fun mapNumber(mapper: (Number) -> Number): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapNumberToObject(mapper: (Number) -> Pair<String, Number>): KJson {
        TODO("Not yet implemented")
    }

    override fun mapBoolean(mapper: (Boolean) -> Boolean): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapBooleanToObject(mapper: (Boolean) -> Pair<String, Boolean>): KJson {
        TODO("Not yet implemented")
    }

    override fun mapNullToString(supplier: () -> String): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapNullToNumber(supplier: () -> Number): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapNullToBoolean(supplier: () -> Boolean): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun mapNullToObject(keyProvider: () -> String): KJson {
        TODO("Not yet implemented")
    }

    override fun mapToArray(): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun flatMapNull(mapper: () -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMapString(mapper: (String) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMapNumber(mapper: (Number) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMapBoolean(mapper: (Boolean) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun filterString(condition: (String) -> Boolean): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun filterNumber(condition: (Number) -> Boolean): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun filterBoolean(condition: (Boolean) -> Boolean): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun getString(): String? {
        TODO("Not yet implemented")
    }

    override fun getStringOrElse(alternative: String): String {
        TODO("Not yet implemented")
    }

    override fun getStringOrElseGet(supplier: () -> String): String {
        TODO("Not yet implemented")
    }

    override fun getNumber(): Number? {
        TODO("Not yet implemented")
    }

    override fun getNumberOrElse(alternative: Number): Number {
        TODO("Not yet implemented")
    }

    override fun getNumberOrElseGet(supplier: () -> Number): Number {
        TODO("Not yet implemented")
    }

    override fun getBoolean(): Boolean? {
        TODO("Not yet implemented")
    }

    override fun getBooleanOrElse(alternative: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun getBooleanOrElseGet(supplier: () -> Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun <O> foldScalar(
        stringHandler: (String) -> O,
        numberHandler: (Number) -> O,
        booleanHandler: (Boolean) -> O,
        nullHandler: () -> O
    ): O {
        TODO("Not yet implemented")
    }
}
