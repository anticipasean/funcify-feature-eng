package funcify.feature.schema.datasource

import kotlinx.collections.immutable.ImmutableSet

/**
 * Represents a type containing, or set of, [funcify.feature.schema.datasource.ParameterAttribute]s
 * that can or must be passed to a given [funcify.feature.schema.datasource.DataElementSource] in order to
 * obtain a value or values for a given [funcify.feature.schema.datasource.SourceIndex]
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterContainerType<SI : SourceIndex<SI>, out A : ParameterAttribute<SI>> :
    ParameterIndex<SI> {

    val parameterAttributes: ImmutableSet<A>

    fun getParameterAttributeWithName(name: String): A?
}
