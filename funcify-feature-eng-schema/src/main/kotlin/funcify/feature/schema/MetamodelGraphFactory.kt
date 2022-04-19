package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 4/3/22
 */
interface MetamodelGraphFactory {

    fun createMetamodelGraph(): DataSourceSpec

    interface DataSourceSpec {

        fun <SI : SourceIndex> includingDataSource(dataSource: DataSource<SI>): DataSourceSpec
        fun build(): Try<MetamodelGraph>
    }
}
