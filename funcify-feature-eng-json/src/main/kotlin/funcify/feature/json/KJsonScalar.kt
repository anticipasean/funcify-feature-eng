package funcify.feature.json

interface KJsonScalar : KJson {

    override fun isScalar(): Boolean {
        return true
    }

    override fun isContainer(): Boolean {
        return false
    }

    fun isString(): Boolean

    fun isNumber(): Boolean

    fun isBoolean(): Boolean

    fun isNull(): Boolean

    fun mapString(mapper: (String) -> String): KJsonScalar

    fun mapStringToObject(mapper: (String) -> Pair<String, String>): KJson

    fun mapNumber(mapper: (Number) -> Number): KJsonScalar

    fun mapNumberToObject(mapper: (Number) -> Pair<String, Number>): KJson

    fun mapBoolean(mapper: (Boolean) -> Boolean): KJsonScalar

    fun mapBooleanToObject(mapper: (Boolean) -> Pair<String, Boolean>): KJson

    fun mapNullToString(supplier: () -> String): KJsonScalar

    fun mapNullToNumber(supplier: () -> Number): KJsonScalar

    fun mapNullToBoolean(supplier: () -> Boolean): KJsonScalar

    fun mapNullToObject(keyProvider: () -> String): KJson

    fun mapToArray(): KJsonContainer

    fun flatMapNull(mapper: () -> KJson): KJson

    fun flatMapString(mapper: (String) -> KJson): KJson

    fun flatMapNumber(mapper: (Number) -> KJson): KJson

    fun flatMapBoolean(mapper: (Boolean) -> KJson): KJson

    fun filterString(condition: (String) -> Boolean): KJsonScalar

    fun filterNumber(condition: (Number) -> Boolean): KJsonScalar

    fun filterBoolean(condition: (Boolean) -> Boolean): KJsonScalar

    fun getString(): String?

    fun getStringOrElse(alternative: String): String

    fun getStringOrElseGet(supplier: () -> String): String

    fun getNumber(): Number?

    fun getNumberOrElse(alternative: Number): Number

    fun getNumberOrElseGet(supplier: () -> Number): Number

    fun getBoolean(): Boolean?

    fun getBooleanOrElse(alternative: Boolean): Boolean

    fun getBooleanOrElseGet(supplier: () -> Boolean): Boolean

    fun <O> foldScalar(
        stringHandler: (String) -> O,
        numberHandler: (Number) -> O,
        booleanHandler: (Boolean) -> O,
        nullHandler: () -> O
    ): O
}
