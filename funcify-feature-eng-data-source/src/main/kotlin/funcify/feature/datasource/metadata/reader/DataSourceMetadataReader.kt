package funcify.feature.datasource.metadata.reader

import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
interface DataSourceMetadataReader<in T, SI : SourceIndex> {

    fun readSourceMetamodelFromMetadata(input: T): SourceMetamodel<SI>

}
