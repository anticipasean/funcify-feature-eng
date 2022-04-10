package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceIndex


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RestApiSourceIndex : SourceIndex {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.REST_API


}