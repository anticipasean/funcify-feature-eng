package funcify.feature.json.design

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.json.KJson
import funcify.feature.json.KJsonContainer
import funcify.feature.json.KJsonScalar
import funcify.feature.json.data.KJsonData
import funcify.feature.json.behavior.KJsonBehavior

internal interface KJsonDesign<SWT, I> : KJson {

    val behavior: KJsonBehavior<SWT>

    val data: KJsonData<SWT, I>

    override fun filterScalar(condition: (KJsonScalar) -> Boolean): KJson {
        TODO("Not yet implemented")
    }

    override fun filterContainer(condition: (KJsonContainer) -> Boolean): KJson {
        TODO("Not yet implemented")
    }

    override fun mapScalar(mapper: (KJsonScalar) -> KJsonScalar): KJson {
        TODO("Not yet implemented")
    }

    override fun mapScalarToContainer(mapper: (KJsonScalar) -> KJsonContainer): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapScalarToArray(): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapScalarToObject(
        mapper: (KJsonScalar) -> Pair<String, KJsonScalar>
    ): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun mapContainer(mapper: (KJsonContainer) -> KJsonContainer): KJson {
        TODO("Not yet implemented")
    }

    override fun mapContainerToScalar(mapper: (KJsonContainer) -> KJsonScalar): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun map(mapper: (KJson) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMapScalar(mapper: (KJsonScalar) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMapContainer(mapper: (KJsonContainer) -> KJson): KJson {
        TODO("Not yet implemented")
    }

    override fun flatMap(mapper: (KJson) -> KJsonContainer): KJson {
        TODO("Not yet implemented")
    }

    override fun getScalar(): KJsonScalar? {
        TODO("Not yet implemented")
    }

    override fun getScalarOrElse(alternative: KJsonScalar): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun getScalarOrElseGet(supplier: () -> KJsonScalar): KJsonScalar {
        TODO("Not yet implemented")
    }

    override fun getContainer(): KJsonContainer? {
        TODO("Not yet implemented")
    }

    override fun getContainerOrElse(alternative: KJsonContainer): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun getContainerOrElseGet(supplier: () -> KJsonContainer): KJsonContainer {
        TODO("Not yet implemented")
    }

    override fun toJacksonJsonNode(): JsonNode {
        TODO("Not yet implemented")
    }

    override fun <O> foldKJson(
        scalarHandler: (KJsonScalar) -> O,
        containerHandler: (KJsonContainer) -> O
    ): O {
        TODO("Not yet implemented")
    }

    fun <WT> fold(template: KJsonBehavior<WT>): KJsonData<WT, I>
}
