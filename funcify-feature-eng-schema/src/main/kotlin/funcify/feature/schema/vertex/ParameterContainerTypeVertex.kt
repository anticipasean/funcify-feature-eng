package funcify.feature.schema.vertex

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeParameterContainerType

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterContainerTypeVertex : SchematicVertex {

    val compositeParameterContainerType: CompositeParameterContainerType
}
