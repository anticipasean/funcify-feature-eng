package funcify.feature.graph.data

import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal data class ParallelizableEdgeDirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
) : GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {

    companion object {
        enum class ParallelizableEdgeDirectedGraphWT

        fun <P, V, E> narrow(
            container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
        ): ParallelizableEdgeDirectedGraphData<P, V, E> {
            return container as ParallelizableEdgeDirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>.narrowed():
            ParallelizableEdgeDirectedGraphData<P, V, E> {
            return ParallelizableEdgeDirectedGraphData.narrow(this)
        }
    }
}
