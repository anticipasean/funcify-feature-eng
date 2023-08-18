package funcify.feature.schema.sdl

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-29
 */
fun interface TypeDefinitionRegistryFilter {

    companion object {
        val INCLUDE_ALL_DEFINITIONS: TypeDefinitionRegistryFilter =
            TypeDefinitionRegistryFilter { tdr: TypeDefinitionRegistry ->
                Result.success(tdr)
            }
    }

    fun filter(typeDefinitionRegistry: TypeDefinitionRegistry): Result<TypeDefinitionRegistry>
}
