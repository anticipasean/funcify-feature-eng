package funcify.feature.schema.sdl.transformer

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger

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
    ): Result<TypeDefinitionRegistry> {
        logger.debug(
            "filter: [ type_definition_registry.types.size: {} ]",
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
            .fold(Result.success(typeDefinitionRegistry)) {
                r: Result<TypeDefinitionRegistry>,
                f: TypeDefinitionRegistryTransformer ->
                try {
                    when {
                        r.isSuccess -> {
                            f.transform(r.getOrThrow())
                        }
                        else -> {
                            r
                        }
                    }
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }
    }
}
