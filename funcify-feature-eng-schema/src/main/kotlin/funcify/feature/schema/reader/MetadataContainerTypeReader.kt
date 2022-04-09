package funcify.feature.schema.reader

import funcify.feature.schema.datasource.SourceContainerType


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
interface MetadataContainerTypeReader<in T> {

    fun readSourceContainerTypesFromMetadata(input: T): Iterable<SourceContainerType<*>>

}