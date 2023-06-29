package funcify.feature.datasource.graphql

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-29
 */
interface ServiceBackedDataElementSource : GraphQLApiDataElementSource {

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

    val graphQLApiService: GraphQLApiService
}
