package funcify.feature.graph.container

import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeGraph.Companion.ParallelizableEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal object PersistentGraphContainerFactory {

    fun <P, V, E> PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>.narrowed():
        TwoToOnePathToEdgeGraph<P, V, E> {
        return TwoToOnePathToEdgeGraph.narrow(this)
    }

    fun <P, V, E> PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>.narrowed():
        ParallelizableEdgeGraph<P, V, E> {
        return ParallelizableEdgeGraph.narrow(this)
    }

    internal class TwoToOnePathToEdgeGraph<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ) : PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {

        companion object {
            enum class TwoToOnePathToEdgeGraphWT

            fun <P, V, E> narrow(
                persistentGraphContainer:
                    PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
            ): TwoToOnePathToEdgeGraph<P, V, E> {
                return persistentGraphContainer as TwoToOnePathToEdgeGraph<P, V, E>
            }
        }
    }

    internal class ParallelizableEdgeGraph<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ) : PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {

        companion object {
            enum class ParallelizableEdgeGraphWT

            fun <P, V, E> narrow(
                persistentGraphContainer:
                    PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
            ): ParallelizableEdgeGraph<P, V, E> {
                return persistentGraphContainer as ParallelizableEdgeGraph<P, V, E>
            }
        }
    }
}
