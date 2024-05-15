package funcify.feature.schema.sdl

import funcify.feature.tools.extensions.PersistentSetExtensions.reduceToPersistentSet
import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentSet

object SDLDefinitionsSetExtractor : (TypeDefinitionRegistry) -> PersistentSet<SDLDefinition<*>> {

    override fun invoke(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): PersistentSet<SDLDefinition<*>> {
        return Stream.concat(
                Stream.of<Iterable<SDLDefinition<*>>>(
                    typeDefinitionRegistry.types().values,
                    typeDefinitionRegistry.directiveDefinitions.values,
                    typeDefinitionRegistry.scalars().values
                ),
                Stream.of<Map<String, List<SDLDefinition<*>>>>(
                        typeDefinitionRegistry.inputObjectTypeExtensions(),
                        typeDefinitionRegistry.interfaceTypeExtensions(),
                        typeDefinitionRegistry.objectTypeExtensions(),
                        typeDefinitionRegistry.unionTypeExtensions(),
                        typeDefinitionRegistry.enumTypeExtensions(),
                        typeDefinitionRegistry.scalarTypeExtensions()
                    )
                    .map(Map<String, List<SDLDefinition<*>>>::values)
                    .flatMap(iterableToStream<List<SDLDefinition<*>>>())
            )
            .parallel()
            .flatMap(iterableToStream<SDLDefinition<*>>())
            .reduceToPersistentSet()
    }

    private fun <T> iterableToStream(): (Iterable<T>) -> Stream<out T> {
        return { i: Iterable<T> ->
            when (i) {
                is Collection<T> -> {
                    i.stream()
                }
                else -> {
                    StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(i.iterator(), 0),
                        false
                    )
                }
            }
        }
    }
}
