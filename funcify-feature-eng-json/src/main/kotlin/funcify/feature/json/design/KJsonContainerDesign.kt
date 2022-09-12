package funcify.feature.json.design

import funcify.feature.json.KJson
import funcify.feature.json.KJsonContainer
import funcify.feature.json.template.KJsonContainerTemplate

internal interface KJsonContainerDesign<WT, I> : KJsonDesign<WT, I>, KJsonContainer {

    override val template: KJsonContainerTemplate<WT>

    override fun isObject(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isArray(): Boolean {
        TODO("Not yet implemented")
    }

    override fun mapObjectKeys(mapper: (String) -> String): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapObjectValues(mapper: (KJson) -> KJson): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapObject(mapper: (String, KJson) -> Pair<String, KJson>): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapArray(mapper: (KJson) -> KJson): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <M : Map<String, KJson>> flatMapObject(
        mapper: (String, KJson) -> M
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <L : List<KJson>> flatMapArray(mapper: (Int, KJson) -> L): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapObjectToArray(mapper: (Int, String, KJson) -> KJson): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapArrayToObject(mapper: (Int, KJson) -> Pair<String, KJson>): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <M : Map<String, KJson>> zipWithMap(
        map: M,
        zipper: (Triple<Int, String?, KJson>, Pair<String, KJson>) -> Pair<String, KJson>,
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <L : List<KJson>> zipWithList(
        list: L,
        zipper: (Triple<Int, String?, KJson>, KJson) -> KJson
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <I : Iterable<KJson>> zipWithIterable(
        iterable: I,
        zipper: (Triple<Int, String?, KJson>, KJson) -> KJson
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <T : Triple<Int, String?, KJson>> zipWithContainer(
        container: KJsonContainer,
        zipper: (T, T) -> T
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <O> foldLeft(initial: O, accumulator: (O, Triple<Int, String?, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldLeftObject(initial: O, accumulator: (O, Pair<String, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldLeftArray(initial: O, accumulator: (O, Pair<Int, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldRight(initial: O, accumulator: (Triple<Int, String?, KJson>, O) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <P : Pair<String?, KJson>> reduceLeft(combiner: (P, P) -> P): P? {
        TODO("Not yet implemented")
    }

    override fun <P : Pair<String?, KJson>> reduceRight(combiner: (P, P) -> P): P? {
        TODO("Not yet implemented")
    }

    override fun <M : Map<String, KJson>, L : List<KJson>, O> foldContainer(
        objectHandler: (M) -> O,
        arrayHandler: (L) -> O
    ): O {
        TODO("Not yet implemented")
    }
}
