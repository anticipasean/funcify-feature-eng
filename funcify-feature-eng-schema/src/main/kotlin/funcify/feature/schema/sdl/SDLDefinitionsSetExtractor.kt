package funcify.feature.schema.sdl

import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

object SDLDefinitionsSetExtractor : (TypeDefinitionRegistry) -> PersistentSet<SDLDefinition<*>> {

    override fun invoke(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): PersistentSet<SDLDefinition<*>> {
        return typeDefinitionRegistry.parseOrder.inOrder
            .asSequence()
            .flatMap { (_: String, tds: List<SDLDefinition<*>>) -> tds.asSequence() }
            .fold(persistentSetOf<SDLDefinition<*>>()) {
                ps: PersistentSet<SDLDefinition<*>>,
                sd: SDLDefinition<*> ->
                ps.add(sd)
            }
    }
}
