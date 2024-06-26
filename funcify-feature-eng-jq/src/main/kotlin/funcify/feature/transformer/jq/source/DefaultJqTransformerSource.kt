package funcify.feature.transformer.jq.source

import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import funcify.feature.transformer.jq.source.callable.DefaultJqTransformerCallableBuilder
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultJqTransformerSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>,
    override val jqTransformersByName: PersistentMap<String, JqTransformer> = persistentMapOf(),
) : JqTransformerSource {

    override fun builder(): TransformerCallable.Builder {
        return DefaultJqTransformerCallableBuilder(jqTransformersByName = jqTransformersByName)
    }
}
