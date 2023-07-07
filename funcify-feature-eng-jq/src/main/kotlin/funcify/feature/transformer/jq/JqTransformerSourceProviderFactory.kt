package funcify.feature.transformer.jq

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSourceProviderFactory {

    fun builder(): JqTransformerSourceProvider.Builder
}
