package funcify.feature.datasource.graphql

import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.dataelementsource.RawSourceType
import funcify.feature.schema.dataelementsource.SourceType
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataElementSource : DataElementSource {

    override val sourceType: SourceType
        get() = RawSourceType.GRAPHQL_API

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
