package funcify.feature.transformer.jq.metadata

import funcify.feature.transformer.jq.JacksonJqTransformer
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
interface JQTransformerReader<in R> {

    fun readTransformers(resource: R): Mono<out List<JacksonJqTransformer>>
}
