package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.dataelementsource.SourceType
import funcify.feature.schema.dataelementsource.RawSourceType

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataElementSource : DataElementSource<RestApiSourceIndex> {

    override val sourceType: SourceType
        get() = RawSourceType.REST_API

    override val name: String

    val restApiService: RestApiService

    override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>

}
