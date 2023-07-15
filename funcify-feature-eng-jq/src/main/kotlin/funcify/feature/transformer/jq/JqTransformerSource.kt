package funcify.feature.transformer.jq

import funcify.feature.schema.transformer.TransformerSource
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSource : TransformerSource {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    val jqTransformersByName: ImmutableMap<String, JqTransformer>
}
