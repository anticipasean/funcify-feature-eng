package funcify.feature.schema.sdl.transformer

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-29
 */
fun interface TypeDefinitionRegistryTransformer {

    companion object {
        val INCLUDE_ALL_DEFINITIONS: TypeDefinitionRegistryTransformer =
            TypeDefinitionRegistryTransformer { tdr: TypeDefinitionRegistry ->
                Result.success(tdr)
            }
    }

    fun transform(typeDefinitionRegistry: TypeDefinitionRegistry): Result<TypeDefinitionRegistry>
}
