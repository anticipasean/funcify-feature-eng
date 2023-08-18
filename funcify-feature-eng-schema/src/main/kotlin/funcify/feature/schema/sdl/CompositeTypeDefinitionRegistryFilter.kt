package funcify.feature.schema.sdl

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-06-30
 */
class CompositeTypeDefinitionRegistryFilter(
    private val typeDefinitionRegistryFilters: List<TypeDefinitionRegistryFilter> = listOf()
) : TypeDefinitionRegistryFilter {

    companion object {
        private val logger: Logger = loggerFor<CompositeTypeDefinitionRegistryFilter>()
    }

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        logger.debug(
            "filter: [ type_definition_registry.types.size: {} ]",
            typeDefinitionRegistry.types().size
        )
        return typeDefinitionRegistryFilters.fold(Result.success(typeDefinitionRegistry)) {
            r: Result<TypeDefinitionRegistry>,
            f: TypeDefinitionRegistryFilter ->
            try {
                when {
                    r.isSuccess -> {
                        f.filter(r.getOrThrow())
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
