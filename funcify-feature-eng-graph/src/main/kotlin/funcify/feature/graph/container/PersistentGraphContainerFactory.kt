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

    internal class DirectedGraph<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ) : PersistentGraphContainer<DirectedGraphWT, P, V, E> {

        companion object {
            enum class DirectedGraphWT

            fun <P, V, E> narrow(
                persistentGraphContainer: PersistentGraphContainer<DirectedGraphWT, P, V, E>
            ): DirectedGraph<P, V, E> {
                return persistentGraphContainer as DirectedGraph<P, V, E>
            }
        }
    }

    internal class ParallelizableEdgeDirectedGraph<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ) : PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {

        companion object {
            enum class ParallelizableEdgeDirectedGraphWT

            fun <P, V, E> narrow(
                persistentGraphContainer:
                    PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
            ): ParallelizableEdgeDirectedGraph<P, V, E> {
                return persistentGraphContainer as ParallelizableEdgeDirectedGraph<P, V, E>
            }
        }
    }
}
