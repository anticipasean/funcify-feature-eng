package funcify.feature.transformer.jq.factory

import funcify.feature.transformer.jq.JqTransformer

/**
 *
 * @author smccarron
 * @created 2023-07-03
 */
interface JqTransformerFactory {

    fun builder(): JqTransformer.Builder

}
