package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.RawSourceType
import funcify.feature.schema.datasource.SourceMetamodel

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
