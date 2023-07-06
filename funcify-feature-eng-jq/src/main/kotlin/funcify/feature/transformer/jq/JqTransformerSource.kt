package funcify.feature.transformer.jq

import funcify.feature.schema.SourceType
import funcify.feature.schema.transformer.TransformerSource
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSource : TransformerSource {

    override val name: String

    override val sourceType: SourceType
        get() = JqSourceType

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

    val transformersByName: ImmutableMap<String, JqTransformer>
}
