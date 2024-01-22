package funcify.feature.schema.sdl.transformer

import graphql.schema.idl.TypeDefinitionRegistry
import org.springframework.core.Ordered
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2024-01-21
 */
interface OrderedTypeDefinitionRegistryTransformer : TypeDefinitionRegistryTransformer, Ordered {

    override fun getOrder(): Int {
        return Ordered.LOWEST_PRECEDENCE
    }

    override fun transform(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Mono<out TypeDefinitionRegistry>
}
