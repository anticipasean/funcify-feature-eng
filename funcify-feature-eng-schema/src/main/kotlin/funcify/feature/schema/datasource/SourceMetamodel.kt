package funcify.feature.schema.datasource

import funcify.feature.schema.SchematicPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 4/9/22
 */
interface SourceMetamodel<SI : SourceIndex> {

    val dataSourceType: DataSourceType

    val sourceIndicesByPath: ImmutableMap<SchematicPath, ImmutableSet<SI>>

}