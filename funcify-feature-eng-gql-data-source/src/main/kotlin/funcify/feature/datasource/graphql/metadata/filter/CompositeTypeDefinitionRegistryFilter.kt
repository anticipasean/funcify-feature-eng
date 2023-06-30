package funcify.feature.datasource.graphql.metadata.filter

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-30
 */
internal class CompositeTypeDefinitionRegistryFilter(
    private val typeDefinitionRegistryFilters: List<TypeDefinitionRegistryFilter> = listOf()
) : TypeDefinitionRegistryFilter {

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        return typeDefinitionRegistryFilters.fold(Result.success(typeDefinitionRegistry)) {
            r: Result<TypeDefinitionRegistry>,
            f: TypeDefinitionRegistryFilter ->
            when {
                r.isSuccess -> {
                    f.filter(r.getOrThrow())
                }
                else -> {
                    r
                }
            }
        }
    }
}
