package funcify.feature.transformer.jq

/**
 * @author smccarron
 * @created 2023-07-03
 */
interface JqTransformerFactory {

    fun builder(): JqTransformer.Builder
}
