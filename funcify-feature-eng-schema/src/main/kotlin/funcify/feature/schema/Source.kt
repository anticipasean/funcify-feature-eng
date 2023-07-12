package funcify.feature.schema

import graphql.schema.idl.TypeDefinitionRegistry

/**
 *
 * @author smccarron
 * @created 2023-07-11
 */
interface Source {

    val name: String

    val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

}
