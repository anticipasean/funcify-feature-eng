package funcify.feature.schema.sdl.transformer

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.foldMapIntoMono
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-30
 */
class CompositeTypeDefinitionRegistryTransformer(
    private val typeDefinitionRegistryTransformers: List<TypeDefinitionRegistryTransformer> =
        listOf()
) : TypeDefinitionRegistryTransformer {

    companion object {
        private val logger: Logger = loggerFor<CompositeTypeDefinitionRegistryTransformer>()
    }

    override fun transform(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Mono<out TypeDefinitionRegistry> {
        logger.debug(
            "transform: [ type_definition_registry.types.size: {} ]",
            typeDefinitionRegistry.types().size
        )
        return typeDefinitionRegistryTransformers
            .asSequence()
            .sortedBy { tdrt: TypeDefinitionRegistryTransformer ->
                when (tdrt) {
                    is OrderedTypeDefinitionRegistryTransformer -> tdrt.order
                    else -> 0
                }
            }
            .foldMapIntoMono(typeDefinitionRegistry) {
                tdr: TypeDefinitionRegistry,
                t: TypeDefinitionRegistryTransformer ->
                t.transform(tdr)
            }
    }
}
