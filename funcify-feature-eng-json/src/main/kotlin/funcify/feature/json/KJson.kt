package funcify.feature.json

interface KJson {

    fun mapString(mapper: (String) -> String): KJson

    fun mapNumeric(mapper: (Number) -> Number): KJson

    fun mapBoolean(mapper: (Boolean) -> Boolean): KJson

    fun <M : Map<String, KJson>> mapJsonObject(mapper: (M) -> M): KJson

    fun <L : List<KJson>> mapJsonArray(mapper: (L) -> L): KJson

    fun flatMapString(mapper: (String) -> KJson): KJson

    fun flatMapNumeric(mapper: (Number) -> KJson): KJson

    fun flatMapBoolean(mapper: (Boolean) -> KJson): KJson

    fun <M : Map<String, KJson>> flatMapJsonObject(mapper: (M) -> KJson): KJson

    fun <L : List<KJson>> flatMapJsonArray(mapper: (L) -> KJson): KJson

    fun <M : Map<String, KJson>> zipIntoKJsonObject(
        keyValueEntry: Map.Entry<String, KJson>,
        scalarFold: (M, Map.Entry<String, KJson>) -> M,
        containerFold: (M, M) -> M,
    ): KJson

    fun <M : Map<String, KJson>> zipIntoKJsonObject(
        keyValuePair: Pair<String, KJson>,
        scalarFold: (M, Pair<String, KJson>) -> M,
        containerFold: (M, M) -> M,
    ): KJson

    fun <L : List<KJson>> zipIntoKJsonArray(
        other: KJson,
        scalarFold: (L, KJson) -> L,
        containerFold: (L, L) -> L,
    ): KJson
}
