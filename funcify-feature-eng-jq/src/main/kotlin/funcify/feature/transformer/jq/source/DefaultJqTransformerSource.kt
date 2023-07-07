package funcify.feature.transformer.jq.source

import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultJqTransformerSource(
    override val name: String,
    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry(),
    override val jqTransformersByName: PersistentMap<String, JqTransformer> = persistentMapOf(),
) : JqTransformerSource
