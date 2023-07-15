package funcify.feature.schema

import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-11
 */
interface Source {

    val name: String

    val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>
}
