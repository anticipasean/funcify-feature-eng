package funcify.feature.schema.dataelement

import funcify.feature.schema.Source
import graphql.schema.idl.TypeDefinitionRegistry

interface DataElementSource : Source {

    override val name: String

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry
}
