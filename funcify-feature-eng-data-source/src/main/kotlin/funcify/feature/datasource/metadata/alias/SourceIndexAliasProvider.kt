package funcify.feature.datasource.metadata.alias

import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap

/**
 * Provide alternate names for the source indices referenced by specific paths in the
 * [SourceMetamodel]
 *
 * @author smccarron
 * @created 2022-07-21
 */
interface SourceIndexAliasProvider<SI : SourceIndex<SI>> {

    fun getAliasesForAttributePaths(
        sourceMetamodel: SourceMetamodel<SI>
    ): ImmutableMap<SchematicPath, String>
}
