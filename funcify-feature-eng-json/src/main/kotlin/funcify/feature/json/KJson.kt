package funcify.feature.json

import com.fasterxml.jackson.databind.JsonNode

interface KJson {

    fun isScalar(): Boolean

    fun isContainer(): Boolean

    fun filterScalar(condition: (KJsonScalar) -> Boolean): KJson

    fun filterContainer(condition: (KJsonContainer) -> Boolean): KJson

    fun mapScalar(mapper: (KJsonScalar) -> KJsonScalar): KJson

    fun mapScalarToContainer(mapper: (KJsonScalar) -> KJsonContainer): KJsonContainer

    fun mapScalarToArray(): KJsonContainer

    fun mapScalarToObject(mapper: (KJsonScalar) -> Pair<String, KJsonScalar>): KJsonContainer

    fun mapContainer(mapper: (KJsonContainer) -> KJsonContainer): KJson

    fun mapContainerToScalar(mapper: (KJsonContainer) -> KJsonScalar): KJsonScalar

    fun map(mapper: (KJson) -> KJson): KJson

    fun flatMapScalar(mapper: (KJsonScalar) -> KJson): KJson

    fun flatMapContainer(mapper: (KJsonContainer) -> KJson): KJson

    fun flatMap(mapper: (KJson) -> KJsonContainer): KJson

    fun getScalar(): KJsonScalar?

    fun getScalarOrElse(alternative: KJsonScalar): KJsonScalar

    fun getScalarOrElseGet(supplier: () -> KJsonScalar): KJsonScalar

    fun getContainer(): KJsonContainer?

    fun getContainerOrElse(alternative: KJsonContainer): KJsonContainer

    fun getContainerOrElseGet(supplier: () -> KJsonContainer): KJsonContainer

    fun toJacksonJsonNode(): JsonNode

    fun <O> foldKJson(scalarHandler: (KJsonScalar) -> O, containerHandler: (KJsonContainer) -> O): O
}
