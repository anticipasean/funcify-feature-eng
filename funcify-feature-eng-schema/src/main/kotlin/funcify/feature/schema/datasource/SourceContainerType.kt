package funcify.feature.schema.datasource

import kotlinx.collections.immutable.ImmutableSet

/**
 * A path, name, and type that represents a container received from a source type
 * @author smccarron
 * @created 1/30/22
 */
interface SourceContainerType<SI : SourceIndex<SI>, out A : SourceAttribute<SI>> : SourceIndex<SI> {

    /** A set since order is not guaranteed */
    val sourceAttributes: ImmutableSet<A>

    fun getSourceAttributeWithName(name: String): A?
}
