package funcify.feature.schema.vertex

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeAttribute

/**
 * Represents a feature function within a graph that uses the context of its parent type vertex for
 * meaning e.g. `user/user_id` where `user_id` is an attribute of the parent type vertex `user`
 * @author smccarron
 * @created 1/30/22
 */
interface AttributeVertex : SchematicVertex {

    val compositeAttribute: CompositeAttribute
}
