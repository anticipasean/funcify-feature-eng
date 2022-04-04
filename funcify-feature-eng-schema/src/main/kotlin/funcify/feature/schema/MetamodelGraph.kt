package funcify.feature.schema

import kotlinx.collections.immutable.PersistentMap


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface MetamodelGraph {

    val schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>

}