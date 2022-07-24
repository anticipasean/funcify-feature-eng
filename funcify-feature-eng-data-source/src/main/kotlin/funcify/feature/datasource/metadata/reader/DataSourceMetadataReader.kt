package funcify.feature.datasource.metadata.reader

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel

/**
 *
 * @author smccarron
 * @created 4/8/22
 */
fun interface DataSourceMetadataReader<in MD, SI : SourceIndex<SI>> {

    fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataSource.Key<SI>,
        metadataInput: MD
    ): SourceMetamodel<SI>
}
