package funcify.feature.json

interface KJson {

    fun isScalar(): Boolean

    fun isContainer(): Boolean

    fun filterScalar(condition: (KJsonScalar) -> Boolean): KJson

    fun filterContainer(condition: (KJsonContainer) -> Boolean): KJson

    fun mapScalar(mapper: (KJsonScalar) -> KJsonScalar): KJson

    fun mapContainer(mapper: (KJsonContainer) -> KJsonContainer): KJson

    fun flatMapScalar(mapper: (KJsonScalar) -> KJson): KJson

    fun flatMapContainer(mapper: (KJsonContainer) -> KJson): KJson
    
}
