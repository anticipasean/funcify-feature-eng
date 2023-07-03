package funcify.feature.transformer.jq.factory

import funcify.feature.transformer.jq.JacksonJqTransformerSourceProvider

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JacksonJqTransformerSourceProviderFactory {

    fun builder(): JacksonJqTransformerSourceProvider.Builder
}
