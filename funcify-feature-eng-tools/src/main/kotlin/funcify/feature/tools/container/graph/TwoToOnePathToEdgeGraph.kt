package funcify.feature.tools.container.graph

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet


/**
 *
 * @author smccarron
 * @created 4/3/22
 */
interface TwoToOnePathToEdgeGraph<P, V, E> : PathBasedGraph<P, V, E> {

    val edgesByPathPair: PersistentMap<Pair<P, P>, E>

    override fun <R> fold(twoToOne: (PersistentMap<P, V>, PersistentMap<Pair<P, P>, E>) -> R,
                          twoToMany: (PersistentMap<P, V>, PersistentMap<Pair<P, P>, PersistentSet<E>>) -> R): R {
        return twoToOne.invoke(verticesByPath,
                               edgesByPathPair)
    }
}