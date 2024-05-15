package funcify.feature.schema.sdl.transformer

import graphql.schema.idl.TypeDefinitionRegistry
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.just

/**
 * @author smccarron
 * @created 2023-06-29
 */
fun interface TypeDefinitionRegistryTransformer {

    companion object {
        val INCLUDE_ALL_DEFINITIONS: TypeDefinitionRegistryTransformer =
            TypeDefinitionRegistryTransformer(::just)
    }

    fun transform(typeDefinitionRegistry: TypeDefinitionRegistry): Mono<out TypeDefinitionRegistry>
}
