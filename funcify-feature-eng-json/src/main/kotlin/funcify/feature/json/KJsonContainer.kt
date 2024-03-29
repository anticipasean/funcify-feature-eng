package funcify.feature.json

interface KJsonContainer : KJson {

    override fun isScalar(): Boolean {
        return false
    }

    override fun isContainer(): Boolean {
        return true
    }

    fun isObject(): Boolean

    fun isArray(): Boolean

    fun mapObjectKeys(mapper: (String) -> String): KJsonContainer

    fun mapObjectValues(mapper: (KJson) -> KJson): KJsonContainer

    fun mapObject(mapper: (String, KJson) -> Pair<String, KJson>): KJsonContainer

    fun mapArray(mapper: (KJson) -> KJson): KJsonContainer

    fun <M : Map<String, KJson>> flatMapObject(mapper: (String, KJson) -> M): KJsonContainer

    fun <L : List<KJson>> flatMapArray(mapper: (Int, KJson) -> L): KJsonContainer

    fun mapObjectToArray(mapper: (Int, String, KJson) -> KJson): KJsonContainer

    fun mapArrayToObject(mapper: (Int, KJson) -> Pair<String, KJson>): KJsonContainer

    fun <M : Map<String, KJson>> zipContainerWithMap(
        map: M,
        zipper: (Triple<Int, String?, KJson>, Pair<String, KJson>) -> Pair<String, KJson>
    ): KJsonContainer

    fun <L : List<KJson>> zipContainerWithList(
        list: L,
        zipper: (Triple<Int, String?, KJson>, KJson) -> KJson
    ): KJsonContainer

    fun <I : Iterable<KJson>> zipWithIterable(
        iterable: I,
        zipper: (Triple<Int, String?, KJson>, KJson) -> KJson
    ): KJsonContainer

    fun <T : Triple<Int, String?, KJson>> zipWithContainer(
        container: KJsonContainer,
        zipper: (T, T) -> T
    ): KJsonContainer

    fun <O> foldContainerLeft(initial: O, accumulator: (O, Triple<Int, String?, KJson>) -> O): O

    fun <O> foldLeftObject(initial: O, accumulator: (O, Pair<String, KJson>) -> O): O

    fun <O> foldLeftArray(initial: O, accumulator: (O, Pair<Int, KJson>) -> O): O

    fun <O> foldContainerRight(initial: O, accumulator: (Triple<Int, String?, KJson>, O) -> O): O

    fun <P : Pair<String?, KJson>> reduceContainerLeft(combiner: (P, P) -> P): P?

    fun <P : Pair<String?, KJson>> reduceContainerRight(combiner: (P, P) -> P): P?

    fun <M : Map<String, KJson>, L : List<KJson>, O> foldContainer(
        objectHandler: (M) -> O,
        arrayHandler: (L) -> O
    ): O
}
