package funcify.feature.transformer.jq

import funcify.feature.schema.transformer.TransformerSource
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSource : TransformerSource {

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

    val jqTransformersByName: ImmutableMap<String, JqTransformer>
}
