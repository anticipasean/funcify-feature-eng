package funcify.feature.schema.vertex

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeParameterAttribute

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterAttributeVertex : SchematicVertex {

    val compositeParameterAttribute: CompositeParameterAttribute

}
