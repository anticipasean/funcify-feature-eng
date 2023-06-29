package funcify.feature.schema.dataelementsource

import graphql.schema.idl.TypeDefinitionRegistry

interface DataElementSource {

    val name: String

    val sourceType: SourceType

    val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
