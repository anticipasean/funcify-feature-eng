package funcify.feature.transformer.jq.source

import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultJqTransformerSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>,
    override val jqTransformersByName: PersistentMap<String, JqTransformer> = persistentMapOf(),
) : JqTransformerSource
