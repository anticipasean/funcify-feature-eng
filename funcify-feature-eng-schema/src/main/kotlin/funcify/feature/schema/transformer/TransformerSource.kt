package funcify.feature.schema.transformer

import funcify.feature.schema.SourceType
import graphql.schema.idl.TypeDefinitionRegistry

/**
 *
 * @author smccarron
 * @created 2023-07-01
 */
interface TransformerSource {

    val name: String

    val sourceType: SourceType

    val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

}
