package funcify.feature.datasource.restapi

import funcify.feature.datasource.RawDataSourceType
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceIndex


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RestApiSourceIndex : SourceIndex {

    override fun getDataSourceType(): DataSourceType {
        return RawDataSourceType.REST_API;
    }

}