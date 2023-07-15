package funcify.feature.datasource.rest

import funcify.feature.schema.dataelement.DataElementSource
import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataElementSource : DataElementSource {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>

    val restApiService: RestApiService
}
