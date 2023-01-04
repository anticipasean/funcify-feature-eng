package funcify.feature.graph.container

import funcify.feature.graph.container.PersistentGraphContainerFactory.DirectedGraph.Companion.DirectedGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph.Companion.ParallelizableEdgeDirectedGraphWT
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal object PersistentGraphContainerFactory {

    fun <P, V, E> PersistentGraphContainer<DirectedGraphWT, P, V, E>.narrowed():
        DirectedGraph<P, V, E> {
        return DirectedGraph.narrow(this)
    }

    fun <P, V, E> PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>.narrowed():
        ParallelizableEdgeDirectedGraph<P, V, E> {
        return ParallelizableEdgeDirectedGraph.narrow(this)
    }

    internal data class DirectedGraph<P, V, E>(
        val verticesByPoint: PersistentMap<P, V>,
        val edgesByPointPair: PersistentMap<Pair<P, P>, E>
    ) : PersistentGraphContainer<DirectedGraphWT, P, V, E> {

        companion object {
            enum class DirectedGraphWT

            fun <P, V, E> narrow(
                container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
            ): DirectedGraph<P, V, E> {
                return container as DirectedGraph<P, V, E>
            }
        }
    }

    internal data class ParallelizableEdgeDirectedGraph<P, V, E>(
        val verticesByPoint: PersistentMap<P, V>,
        val edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ) : PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {

        companion object {
            enum class ParallelizableEdgeDirectedGraphWT

            fun <P, V, E> narrow(
                container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
            ): ParallelizableEdgeDirectedGraph<P, V, E> {
                return container as ParallelizableEdgeDirectedGraph<P, V, E>
            }
        }
    }
}
