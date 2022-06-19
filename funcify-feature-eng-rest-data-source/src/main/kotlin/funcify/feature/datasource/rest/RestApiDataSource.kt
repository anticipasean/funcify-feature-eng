package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceMetamodel

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataSource : DataSource<RestApiSourceIndex> {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.REST_API

    override val name: String

    val restApiService: RestApiService

    override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>

}
