package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.SourceType

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataElementSource : DataElementSource<RestApiSourceIndex> {

    override val sourceType: SourceType
        get() = DataElementSourceType.REST_API

    override val name: String

    val restApiService: RestApiService

    override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>

}
