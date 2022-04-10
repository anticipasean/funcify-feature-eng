package funcify.feature.schema.datasource.reader

import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
interface MetadataReader<in T, SI : SourceIndex> {

    fun readSourceContainerTypesFromMetadata(input: T): SourceMetamodel<SI>

}