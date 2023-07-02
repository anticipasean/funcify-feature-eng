package funcify.feature.schema.dataelement

import funcify.feature.schema.SourceType
import graphql.schema.idl.TypeDefinitionRegistry

interface DataElementSource {

    val name: String

    val sourceType: SourceType

    val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
