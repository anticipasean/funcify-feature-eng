package funcify.feature.transformer.jq.metadata

import graphql.schema.idl.TypeDefinitionRegistry
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
interface JQTransformerReader<in R> {

    fun readMetadata(resource: R): Mono<TypeDefinitionRegistry>

}
