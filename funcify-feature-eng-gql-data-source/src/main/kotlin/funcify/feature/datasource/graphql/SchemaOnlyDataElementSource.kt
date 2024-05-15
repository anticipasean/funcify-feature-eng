package funcify.feature.datasource.graphql

import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-06-29
 */
interface SchemaOnlyDataElementSource : GraphQLApiDataElementSource {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>
}
