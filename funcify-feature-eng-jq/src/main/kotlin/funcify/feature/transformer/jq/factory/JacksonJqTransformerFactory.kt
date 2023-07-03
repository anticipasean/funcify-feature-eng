package funcify.feature.transformer.jq.factory

import funcify.feature.transformer.jq.JacksonJqTransformer

/**
 *
 * @author smccarron
 * @created 2023-07-03
 */
interface JacksonJqTransformerFactory {

    fun builder(): JacksonJqTransformer.Builder

}
