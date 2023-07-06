package funcify.feature.transformer.jq

import funcify.feature.schema.SourceType
import funcify.feature.schema.transformer.TransformerSource
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JacksonJqTransformerSource : TransformerSource {

    override val name: String

    override val sourceType: SourceType
        get() = JacksonJqSourceType

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

    val transformersByName: ImmutableMap<String, JacksonJqTransformer>

}
