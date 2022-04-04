package funcify.feature.tools.container.graph

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface PersistentGraph<V, E> {

    val vertices: PersistentList<V>

    val edgesByConnectedVertices: PersistentMap<Pair<V, V>, PersistentSet<E>>

}