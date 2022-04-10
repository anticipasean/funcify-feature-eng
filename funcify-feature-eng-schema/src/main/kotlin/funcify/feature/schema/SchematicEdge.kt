package funcify.feature.schema

import funcify.feature.schema.path.SchematicPath


/**
 *
 * @author smccarron
 * @created 3/31/22
 */
interface SchematicEdge {

    val id: Pair<SchematicPath, SchematicPath>

}