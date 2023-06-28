package funcify.feature.schema.vertex

import funcify.feature.schema.index.CompositeParameterAttribute
import funcify.feature.schema.index.CompositeParameterContainerType

/**
 * Represents a type containing, or set of, [funcify.feature.schema.datasource.ParameterAttribute]s
 * that can or must be passed to a given [funcify.feature.schema.datasource.DataElementSource] in order to
 * obtain a value or values for a given [funcify.feature.schema.datasource.SourceIndex]
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterJunctionVertex : ParameterContainerTypeVertex, ParameterAttributeVertex {

    override val compositeParameterContainerType: CompositeParameterContainerType

    override val compositeParameterAttribute: CompositeParameterAttribute
}
