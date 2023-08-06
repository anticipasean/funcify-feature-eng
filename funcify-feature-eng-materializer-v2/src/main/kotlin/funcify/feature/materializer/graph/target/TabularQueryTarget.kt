package funcify.feature.materializer.graph.target

import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2023-08-05
 */
interface TabularQueryTarget {

    val outputColumnNames: ImmutableSet<String>

}
