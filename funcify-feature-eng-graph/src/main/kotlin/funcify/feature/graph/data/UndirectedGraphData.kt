package funcify.feature.graph.data

import funcify.feature.graph.data.UndirectedGraphData.Companion.UndirectedGraphDataWT
import funcify.feature.graph.line.UndirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class UndirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesByLine: PersistentMap<UndirectedLine<P>, E>
) : GraphData<UndirectedGraphDataWT, P, V, E> {

    companion object {
        enum class UndirectedGraphDataWT

        fun <P, V, E> narrow(
            container: GraphData<UndirectedGraphDataWT, P, V, E>
        ): UndirectedGraphData<P, V, E> {
            return container as UndirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<UndirectedGraphDataWT, P, V, E>.narrowed():
            UndirectedGraphData<P, V, E> {
            return UndirectedGraphData.narrow(this)
        }

        private val EMPTY: UndirectedGraphData<Any, Any, Any> =
            UndirectedGraphData<Any, Any, Any>(persistentMapOf(), persistentMapOf())

        fun <P, V, E> empty(): UndirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as UndirectedGraphData<P, V, E>
        }
    }
}
