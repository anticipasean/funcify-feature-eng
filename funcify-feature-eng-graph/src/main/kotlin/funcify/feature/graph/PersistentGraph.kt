package funcify.feature.graph

interface PersistentGraph<P, V, E> : ImmutableGraph<P, V, E> {

    fun put(path: P, vertex: V): PersistentGraph<P, V, E>

    fun put(path1: P, path2: P, edge: E): PersistentGraph<P, V, E>

    fun put(pathPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E>

    fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E>

    fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E>

    fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(edges: M): PersistentGraph<P, V, E>

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E>

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E>

    override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E>

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R>

    override fun <R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P, R, E>

    override fun <R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, R>
}
