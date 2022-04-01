package funcify.feature.schema

import funcify.feature.tools.container.graph.PersistentGraph


/**
 *
 * @author smccarron
 * @created 3/31/22
 */
interface MetamodelGraph {

    val graph: PersistentGraph<SchematicPath, SchematicVertex,  SchematicEdge>

}