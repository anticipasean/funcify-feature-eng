package funcify.feature.graph

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
interface GraphBuilder<B : GraphBuilder<B>> {

    fun <B : DirectedGraphBuilder<B>> directed(): DirectedGraphBuilder<B>

    fun <B : UndirectedGraphBuilder<B>> undirected(): UndirectedGraphBuilder<B>

    fun permitSelfLoops(): B

    fun permitParallelEdges(): B

    interface DirectedGraphBuilder<B : DirectedGraphBuilder<B>> : GraphBuilder<B> {

        fun <P, V, E> build(): DirectedPersistentGraph<P, V, E>
    }

    interface UndirectedGraphBuilder<B : UndirectedGraphBuilder<B>> : GraphBuilder<B> {

        fun <P, V, E> build(): PersistentGraph<P, V, E>
    }
}
