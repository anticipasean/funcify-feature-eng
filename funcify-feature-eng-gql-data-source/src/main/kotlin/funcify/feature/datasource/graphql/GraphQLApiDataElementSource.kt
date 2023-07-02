package funcify.feature.datasource.graphql

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.SourceType
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataElementSource : DataElementSource {

    override val sourceType: SourceType
        get() = GraphQLSourceType

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
