package funcify.feature.graph.data

import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData.Companion.ParallelizableEdgeUndirectedGraphWT
import funcify.feature.graph.line.Line
import funcify.feature.graph.line.UndirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal data class ParallelizableEdgeUndirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesSetByLine: PersistentMap<UndirectedLine<P>, PersistentSet<E>>
) : GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {

    companion object {
        enum class ParallelizableEdgeUndirectedGraphWT

        fun <P, V, E> narrow(
            container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>
        ): ParallelizableEdgeUndirectedGraphData<P, V, E> {
            return container as ParallelizableEdgeUndirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>.narrowed():
            ParallelizableEdgeUndirectedGraphData<P, V, E> {
            return ParallelizableEdgeUndirectedGraphData.narrow(this)
        }

        private val EMPTY: ParallelizableEdgeUndirectedGraphData<Any, Any, Any> =
            ParallelizableEdgeUndirectedGraphData<Any, Any, Any>(
                persistentMapOf(),
                persistentMapOf()
            )

        fun <P, V, E> empty(): ParallelizableEdgeUndirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as ParallelizableEdgeUndirectedGraphData<P, V, E>
        }
    }
}
