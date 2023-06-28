package funcify.feature.datasource.metadata.reader

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel

/**
 *
 * @author smccarron
 * @created 4/8/22
 */
fun interface DataSourceMetadataReader<in MD, SI : SourceIndex<SI>> {

    fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataElementSource.Key<SI>,
        metadataInput: MD
    ): SourceMetamodel<SI>
}
