package funcify.feature.schema.datasource

import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SourceContainerType<out A : SourceAttribute> : SourceIndex {

    /**
     * A set since order is not guaranteed
     */
    fun getSourceAttributes(): ImmutableSet<A>

    fun getSourceAttributeByName(): SourceAttribute?

}