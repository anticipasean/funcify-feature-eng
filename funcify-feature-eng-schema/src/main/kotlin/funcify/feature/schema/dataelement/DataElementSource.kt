package funcify.feature.schema.dataelement

import funcify.feature.schema.Source
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.SDLDefinition
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableSet

interface DataElementSource : Source {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    interface Builder {

        fun setDomainSelection(path: GQLOperationPath, fieldDefinition: GraphQLFieldDefinition): Builder

        fun addSelection(path: GQLOperationPath, fieldDefinition: GraphQLFieldDefinition): Builder

        fun addAllSelections(selections: Iterable<Pair<GQLOperationPath, GraphQLFieldDefinition>>): Builder

        fun addAllSelections(selections: Map<GQLOperationPath, GraphQLFieldDefinition>): Builder

        fun build(): DataElementCallable
    }
}
