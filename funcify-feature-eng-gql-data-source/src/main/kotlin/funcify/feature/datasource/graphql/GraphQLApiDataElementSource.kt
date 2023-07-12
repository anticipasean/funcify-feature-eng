package funcify.feature.datasource.graphql

import funcify.feature.schema.dataelement.DataElementSource
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataElementSource : DataElementSource {

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
