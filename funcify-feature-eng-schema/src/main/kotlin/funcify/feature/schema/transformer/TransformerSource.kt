package funcify.feature.schema.transformer

import funcify.feature.schema.Source
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSource : Source, TransformerCallableFactory {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    override fun builder(): TransformerCallable.Builder

}
