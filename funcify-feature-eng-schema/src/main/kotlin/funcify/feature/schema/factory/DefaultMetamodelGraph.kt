package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap

/**
 *
 * @author smccarron
 * @created 4/2/22
 */
data class DefaultMetamodelGraph(
    override val dataSourcesByName: PersistentMap<String, DataSource<*>>,
    override val schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>,
) : MetamodelGraph {}
