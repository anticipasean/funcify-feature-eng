package funcify.feature.schema.impl

import funcify.feature.schema.CompositeSchema
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.SchematicVertex
import kotlinx.collections.immutable.PersistentMap


/**
 *
 * @author smccarron
 * @created 4/2/22
 */
data class DefaultCompositeSchema(override val vertices: PersistentMap<SchematicPath, SchematicVertex>): CompositeSchema {

}
