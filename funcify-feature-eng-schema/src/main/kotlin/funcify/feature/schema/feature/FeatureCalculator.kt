package funcify.feature.schema.feature

import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface FeatureCalculator {

    val name: String

    val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
