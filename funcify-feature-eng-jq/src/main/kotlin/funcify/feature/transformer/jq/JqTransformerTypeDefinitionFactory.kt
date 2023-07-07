package funcify.feature.transformer.jq

import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-07-06
 */
fun interface JqTransformerTypeDefinitionFactory {

    fun createTypeDefinitionRegistry(
        environment: JqTransformerTypeDefinitionEnvironment
    ): Result<TypeDefinitionRegistry>
}
