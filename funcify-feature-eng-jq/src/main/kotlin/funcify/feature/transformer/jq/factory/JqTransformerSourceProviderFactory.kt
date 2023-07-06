package funcify.feature.transformer.jq.factory

import funcify.feature.transformer.jq.JqTransformerSourceProvider

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSourceProviderFactory {

    fun builder(): JqTransformerSourceProvider.Builder
}
