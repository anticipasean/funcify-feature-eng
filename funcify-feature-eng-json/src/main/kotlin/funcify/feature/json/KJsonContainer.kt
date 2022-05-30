package funcify.feature.json

interface KJsonContainer : KJson {

    fun isObject(): Boolean

    fun isArray(): Boolean

    fun mapObject(mapper: (String, KJson) -> Pair<String, KJson>): KJson

    fun mapArray(mapper: (Int, KJson) -> Pair<Int, KJson>): KJson

    fun flatMapObject(mapper: (String, KJson) -> KJson): KJson

    fun flatMapArray(mapper: (Int, KJson) -> KJson): KJson

    fun <M : Map<String, KJson>> zipWithMap(
        map: M,
        zipper: (Pair<String, KJson>, Pair<String, KJson>) -> Pair<String, KJson>
    ): KJson

    fun <L : List<KJson>> zipWithList(list: L, zipper: (KJson, KJson) -> KJson): KJson

    fun <I : Iterable<KJson>> zipWithIterable(iterable: I, zipper: (KJson, KJson) -> KJson): KJson

    fun <O> foldLeft(
        initial: O,
        foldObject: (O, Pair<String, KJson>) -> O,
        foldArray: (O, Pair<Int, KJson>) -> O
    ): O

    fun <O> foldLeftObject(initial: O, fold: (O, Pair<String, KJson>) -> O): O

    fun <O> foldLeftArray(initial: O, fold: (O, Pair<Int, KJson>) -> O): O
}
