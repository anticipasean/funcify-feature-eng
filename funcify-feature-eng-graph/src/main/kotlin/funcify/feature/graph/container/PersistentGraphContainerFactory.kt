package funcify.feature.graph.container

import funcify.feature.graph.container.PersistentGraphContainerFactory.PersistentGraphStream.Companion.GraphStreamWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph.Companion.TwoToManyPathToEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal object PersistentGraphContainerFactory {

    fun <P, V, E> PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>.narrowed():
        TwoToOnePathToEdgeGraph<P, V, E> {
        return TwoToOnePathToEdgeGraph.narrow(this)
    }

    fun <P, V, E> PersistentGraphContainer<TwoToManyPathToEdgeGraphWT, P, V, E>.narrowed():
        TwoToManyPathToEdgeGraph<P, V, E> {
        return TwoToManyPathToEdgeGraph.narrow(this)
    }

    fun <P, V, E> PersistentGraphContainer<GraphStreamWT, P, V, E>.narrowed():
        PersistentGraphStream<P, V, E> {
        return PersistentGraphStream.narrow(this)
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

    internal class TwoToManyPathToEdgeGraph<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ) : PersistentGraphContainer<TwoToManyPathToEdgeGraphWT, P, V, E> {

        companion object {
            enum class TwoToManyPathToEdgeGraphWT

            fun <P, V, E> narrow(
                persistentGraphContainer:
                    PersistentGraphContainer<TwoToManyPathToEdgeGraphWT, P, V, E>
            ): TwoToManyPathToEdgeGraph<P, V, E> {
                return persistentGraphContainer as TwoToManyPathToEdgeGraph<P, V, E>
            }
        }
    }

    internal class PersistentGraphStream<P, V, E>(
        val verticesByPathStream: Stream<out Pair<P, V>>,
        val edgesByPathPairStream: Stream<out Pair<Pair<P, P>, E>>
    ) : PersistentGraphContainer<GraphStreamWT, P, V, E> {

        companion object {
            enum class GraphStreamWT

            fun <P, V, E> narrow(
                persistentGraphContainer: PersistentGraphContainer<GraphStreamWT, P, V, E>
            ): PersistentGraphStream<P, V, E> {
                return persistentGraphContainer as PersistentGraphStream<P, V, E>
            }
        }
    }
}
