package funcify.feature.file.metadata

import graphql.schema.idl.TypeDefinitionRegistry
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-08-16
 */
fun interface FileRegistryMetadataProvider<in R> {

    fun provideTypeDefinitionRegistry(resource: R): Mono<TypeDefinitionRegistry>

}
