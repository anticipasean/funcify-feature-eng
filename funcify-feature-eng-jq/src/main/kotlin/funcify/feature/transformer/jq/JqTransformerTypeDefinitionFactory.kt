package funcify.feature.transformer.jq

import funcify.feature.tools.container.attempt.Try
import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-07-06
 */
fun interface JqTransformerTypeDefinitionFactory {

    fun createTypeDefinitionRegistry(
        environment: JqTransformerTypeDefinitionEnvironment
    ): Try<TypeDefinitionRegistry>
}
