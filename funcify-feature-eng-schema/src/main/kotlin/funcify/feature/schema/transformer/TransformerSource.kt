package funcify.feature.schema.transformer

import funcify.feature.schema.Source
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSource : Source {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    fun builder(): Builder

    interface Builder {

        fun setTransformerPath(path: GQLOperationPath): Builder

        fun addArgumentName(name: String): Builder

        fun addAllArgumentNames(names: Iterable<String>): Builder

        fun build(): TransformerCallable
    }
}
