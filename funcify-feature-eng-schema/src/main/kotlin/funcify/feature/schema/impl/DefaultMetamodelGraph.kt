package funcify.feature.schema.impl

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.SchematicVertex
import kotlinx.collections.immutable.PersistentMap


/**
 *
 * @author smccarron
 * @created 4/2/22
 */
data class DefaultMetamodelGraph(override val schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>): MetamodelGraph {

}
