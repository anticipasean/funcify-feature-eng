package funcify.feature.schema.dataelement

/**
 * @author smccarron
 * @created 2023-08-24
 */
interface DataElementCallableFactory {

    fun builder(): DataElementCallable.Builder
}
