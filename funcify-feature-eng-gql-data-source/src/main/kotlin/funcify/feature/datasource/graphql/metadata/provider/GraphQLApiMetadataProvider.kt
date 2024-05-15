package funcify.feature.datasource.graphql.metadata.provider

import graphql.schema.idl.TypeDefinitionRegistry
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 4/4/22
 */
fun interface GraphQLApiMetadataProvider<in R> {

    fun provideTypeDefinitionRegistry(resource: R): Mono<out TypeDefinitionRegistry>
}
