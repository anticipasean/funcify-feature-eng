package funcify.feature.datasource.graphql

import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-06-29
 */
interface ServiceBackedDataElementSource : GraphQLApiDataElementSource {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    val graphQLApiService: GraphQLApiService
}
