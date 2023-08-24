package funcify.feature.schema.transformer

/**
 * @author smccarron
 * @created 2023-08-24
 */
interface TransformerCallableFactory {

    fun builder(): TransformerCallable.Builder
}
