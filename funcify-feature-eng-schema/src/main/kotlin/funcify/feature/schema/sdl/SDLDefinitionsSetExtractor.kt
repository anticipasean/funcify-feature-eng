package funcify.feature.schema.sdl

import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

object SDLDefinitionsSetExtractor : (TypeDefinitionRegistry) -> PersistentSet<SDLDefinition<*>> {

    override fun invoke(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): PersistentSet<SDLDefinition<*>> {
        return sequenceOf<Iterable<SDLDefinition<*>>>(
                typeDefinitionRegistry.types().values,
                typeDefinitionRegistry.directiveDefinitions.values,
                typeDefinitionRegistry.scalars().values
            )
            .plus(
                sequenceOf<Map<String, List<SDLDefinition<*>>>>(
                        typeDefinitionRegistry.inputObjectTypeExtensions(),
                        typeDefinitionRegistry.interfaceTypeExtensions(),
                        typeDefinitionRegistry.objectTypeExtensions(),
                        typeDefinitionRegistry.unionTypeExtensions(),
                        typeDefinitionRegistry.enumTypeExtensions(),
                        typeDefinitionRegistry.scalarTypeExtensions()
                    )
                    .flatMap(Map<String, List<SDLDefinition<*>>>::values)
            )
            .flatMap(Iterable<SDLDefinition<*>>::asSequence)
            .fold(persistentSetOf<SDLDefinition<*>>(), PersistentSet<SDLDefinition<*>>::add)
    }
}
