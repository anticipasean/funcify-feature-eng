package funcify.feature.transformer.jq.metadata

import funcify.feature.transformer.jq.JqTransformer
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
interface JqTransformerReader<in R> {

    fun readTransformers(resource: R): Mono<out List<JqTransformer>>
}
