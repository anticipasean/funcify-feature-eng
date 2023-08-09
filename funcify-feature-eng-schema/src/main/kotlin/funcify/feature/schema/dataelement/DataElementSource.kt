package funcify.feature.schema.dataelement

import funcify.feature.schema.Source
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

interface DataElementSource : Source {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    interface Builder {

        fun setDomainPath(path: GQLOperationPath): Builder

        fun addQueryOperationPath(path: GQLOperationPath): Builder

        fun addAllQueryOperationPaths(paths: Iterable<GQLOperationPath>): Builder

        fun build(): DataElementCallable
    }
}
