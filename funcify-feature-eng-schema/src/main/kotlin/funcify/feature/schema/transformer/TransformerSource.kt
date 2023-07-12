package funcify.feature.schema.transformer

import funcify.feature.schema.Source
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSource : Source {

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
