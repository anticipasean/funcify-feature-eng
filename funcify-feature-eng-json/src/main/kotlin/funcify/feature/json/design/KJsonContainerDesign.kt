package funcify.feature.json.design

import funcify.feature.json.KJson
import funcify.feature.json.KJsonContainer
import funcify.feature.json.behavior.KJsonContainerBehavior
import funcify.feature.json.data.KJsonContainerData

internal interface KJsonContainerDesign<WT, I> : KJsonDesign<WT, I>, KJsonContainer {

    override val behavior: KJsonContainerBehavior<WT>

    override val data: KJsonContainerData<WT, I>

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

    override fun <M : Map<String, KJson>> zipContainerWithMap(
        map: M,
        zipper: (Triple<Int, String?, KJson>, Pair<String, KJson>) -> Pair<String, KJson>,
                                                             ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun <L : List<KJson>> zipContainerWithList(
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

    override fun <O> foldContainerLeft(initial: O, accumulator: (O, Triple<Int, String?, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldLeftObject(initial: O, accumulator: (O, Pair<String, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldLeftArray(initial: O, accumulator: (O, Pair<Int, KJson>) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <O> foldContainerRight(initial: O, accumulator: (Triple<Int, String?, KJson>, O) -> O): O {
        TODO("Not yet implemented")
    }

    override fun <P : Pair<String?, KJson>> reduceContainerLeft(combiner: (P, P) -> P): P? {
        TODO("Not yet implemented")
    }

    override fun <P : Pair<String?, KJson>> reduceContainerRight(combiner: (P, P) -> P): P? {
        TODO("Not yet implemented")
    }

    override fun <M : Map<String, KJson>, L : List<KJson>, O> foldContainer(
        objectHandler: (M) -> O,
        arrayHandler: (L) -> O
    ): O {
        TODO("Not yet implemented")
    }
}
