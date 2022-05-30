package funcify.feature.json

interface KJsonScalar : KJson {

    fun isString(): Boolean

    fun isNumeric(): Boolean

    fun isBoolean(): Boolean

    fun isNull(): Boolean

    fun mapString(mapper: (String) -> String): KJson

    fun mapNumeric(mapper: (Number) -> Number): KJson

    fun mapBoolean(mapper: (Boolean) -> Boolean): KJson

    fun flatMapNull(mapper: () -> KJson): KJson

    fun flatMapString(mapper: (String) -> KJson): KJson

    fun flatMapNumeric(mapper: (Number) -> KJson): KJson

    fun flatMapBoolean(mapper: (Boolean) -> KJson): KJson

    fun filterString(condition: (String) -> Boolean): KJson

    fun filterNumeric(condition: (Number) -> Boolean): KJson

    fun filterBoolean(condition: (Boolean) -> Boolean): KJson

}
