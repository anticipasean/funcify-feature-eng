package funcify.feature.schema

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-26
 */
interface DataElementSource {

    // val dataSourceType: DataSourceType

    val typeDefinitionRegistry: TypeDefinitionRegistry
}
