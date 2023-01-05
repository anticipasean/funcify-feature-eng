package funcify.feature.graph

import funcify.feature.graph.context.DirectedPersistentGraphContext

interface PersistentGraph<P, V, E> : ImmutableGraph<P, V, E> {

    companion object {

        fun <P, V, E> empty(): PersistentGraph<P, V, E> {
            return DirectedPersistentGraphContext<P, V, E>()
        }

        fun <P, V, E> of(
            point1: P,
            vertex1: V,
            point2: P,
            vertex2: V,
            edge: E
        ): PersistentGraph<P, V, E> {
            return DirectedPersistentGraphContext<P, V, E>()
                .put(point1, vertex1)
                .put(point2, vertex2)
                .put(point1, point2, edge)
        }
    }

    fun put(point: P, vertex: V): PersistentGraph<P, V, E>

    fun put(point1: P, point2: P, edge: E): PersistentGraph<P, V, E>

    fun put(pointPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E>

    fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E>

    fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E>

    fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(edges: M): PersistentGraph<P, V, E>

    fun remove(point: P): PersistentGraph<P, V, E>

    override fun filterVertices(condition: (P, V) -> Boolean): PersistentGraph<P, V, E>

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E> {
        return filterVertices { _: P, v: V -> condition(v) }
    }

    override fun filterEdges(condition: (Pair<P, P>, E) -> Boolean): PersistentGraph<P, V, E>

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E> {
        return filterEdges { _: Pair<P, P>, e: E -> condition(e) }
    }

    override fun <R> mapPoints(function: (P, V) -> R): PersistentGraph<R, V, E>

    override fun <R> mapPoints(function: (P) -> R): PersistentGraph<R, V, E>

    override fun <R> mapVertices(function: (P, V) -> R): PersistentGraph<P, R, E>

    override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
        return mapVertices { _: P, v: V -> function(v) }
    }

    override fun <R> mapEdges(function: (Pair<P, P>, E) -> R): PersistentGraph<P, V, R>

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
        return mapEdges { ek: Pair<P, P>, e: E -> function(e) }
    }

    override fun <P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P1, V1, E>

    override fun <E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, E1>
}
