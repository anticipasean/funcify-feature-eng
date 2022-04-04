package funcify.feature.tools.container.graph

import arrow.typeclasses.Monoid
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet


/**
 *
 * @author smccarron
 * @created 4/4/22
 */
class PathBasedGraphMonoid<P, V, E> private constructor() : Monoid<PathBasedGraph<P, V, E>>,
                                                            (PathBasedGraph<P, V, E>, PathBasedGraph<P, V, E>) -> PathBasedGraph<P, V, E> {

    companion object {

        private val DEFAULT_INSTANCE: PathBasedGraphMonoid<Any?, Any?, Any?> = PathBasedGraphMonoid()

        fun <P, V, E> getInstance(): PathBasedGraphMonoid<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return DEFAULT_INSTANCE as PathBasedGraphMonoid<P, V, E>
        }
    }

    override fun empty(): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>()
    }

    override fun PathBasedGraph<P, V, E>.combine(b: PathBasedGraph<P, V, E>): PathBasedGraph<P, V, E> {
        return this.fold({ aVerts: PersistentMap<P, V>, aSingleEdges: PersistentMap<Pair<P, P>, E> ->
                             b.fold({ bVerts: PersistentMap<P, V>, bSingleEdges: PersistentMap<Pair<P, P>, E> ->
                                        DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(verticesByPath = aVerts.putAll(bVerts)).putAllEdges(aSingleEdges)
                                                .putAllEdges(bSingleEdges)
                                    },
                                    { bVerts: PersistentMap<P, V>, bManyEdges: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                                        DefaultTwoToManyEdgePathBasedGraph<P, V, E>(verticesByPath = aVerts.putAll(bVerts)).putAllEdges(aSingleEdges)
                                                .putAllEdgeSets(bManyEdges)
                                    })
                         },
                         { aVerts: PersistentMap<P, V>, aManyEdges: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                             b.fold({ bVerts: PersistentMap<P, V>, bSingleEdges: PersistentMap<Pair<P, P>, E> ->
                                        DefaultTwoToManyEdgePathBasedGraph<P, V, E>(verticesByPath = aVerts.putAll(bVerts)).putAllEdgeSets(aManyEdges)
                                                .putAllEdges(bSingleEdges)
                                    },
                                    { bVerts: PersistentMap<P, V>, bManyEdges: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                                        DefaultTwoToManyEdgePathBasedGraph<P, V, E>(verticesByPath = aVerts.putAll(bVerts)).putAllEdgeSets(aManyEdges)
                                                .putAllEdgeSets(bManyEdges)
                                    })

                         })
    }

    override fun invoke(pg1: PathBasedGraph<P, V, E>,
                        pg2: PathBasedGraph<P, V, E>): PathBasedGraph<P, V, E> {
        return pg1.combine(pg2)
    }

}