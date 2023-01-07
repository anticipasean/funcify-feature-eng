package funcify.feature.graph

import funcify.feature.graph.line.Line

interface PersistentGraph<P, V, E> : ImmutableGraph<P, V, E> {

    fun put(point: P, vertex: V): PersistentGraph<P, V, E>

    fun put(point1: P, point2: P, edge: E): PersistentGraph<P, V, E>

    fun put(line: Line<P>, edge: E): PersistentGraph<P, V, E>

    /** Aliases for put methods */
    fun putVertex(point: P, vertex: V): PersistentGraph<P, V, E> {
        return put(point, vertex)
    }

    fun putEdge(point1: P, point2: P, edge: E): PersistentGraph<P, V, E> {
        return put(point1, point2, edge)
    }

    fun putEdge(line: Line<P>, edge: E): PersistentGraph<P, V, E> {
        return put(line, edge)
    }

    fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E>

    fun <M : Map<Line<P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E>

    fun <S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(edges: M): PersistentGraph<P, V, E>

    fun removeVertex(point: P): PersistentGraph<P, V, E>

    fun removeEdges(point1: P, point2: P): PersistentGraph<P, V, E>

    fun removeEdges(line: Line<P>): PersistentGraph<P, V, E>

    override fun filterVertices(condition: (P, V) -> Boolean): PersistentGraph<P, V, E>

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E> {
        return filterVertices { _: P, v: V -> condition(v) }
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): PersistentGraph<P, V, E>

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E> {
        return filterEdges { _: Line<P>, e: E -> condition(e) }
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): PersistentGraph<P1, V, E>

    override fun <P1> mapPoints(function: (P) -> P1): PersistentGraph<P1, V, E>

    override fun <V1> mapVertices(function: (P, V) -> V1): PersistentGraph<P, V1, E>

    override fun <V1> mapVertices(function: (V) -> V1): PersistentGraph<P, V1, E> {
        return mapVertices { _: P, v: V -> function(v) }
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): PersistentGraph<P, V, E1>

    override fun <E1> mapEdges(function: (E) -> E1): PersistentGraph<P, V, E1> {
        return mapEdges { _: Line<P>, e: E -> function(e) }
    }

    override fun <P1, V1, M : Map<P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P1, V1, E>

    override fun <E1, M : Map<Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): PersistentGraph<P, V, E1>
}
