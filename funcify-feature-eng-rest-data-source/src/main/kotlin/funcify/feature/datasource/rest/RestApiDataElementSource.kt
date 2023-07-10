package funcify.feature.datasource.rest

import funcify.feature.schema.SourceType
import funcify.feature.schema.dataelement.DataElementSource
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataElementSource : DataElementSource {

    override val name: String

    override val sourceType: SourceType
        get() = RestApiSourceType

    override val sourceTypeDefinitionRegistry: TypeDefinitionRegistry

    val restApiService: RestApiService
}
