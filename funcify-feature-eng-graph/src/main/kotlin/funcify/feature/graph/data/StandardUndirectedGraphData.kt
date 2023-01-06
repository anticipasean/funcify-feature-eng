package funcify.feature.graph.data

import funcify.feature.graph.data.StandardUndirectedGraphData.Companion.StandardUndirectedGraphDataWT
import funcify.feature.graph.line.UndirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class StandardUndirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesByLine: PersistentMap<UndirectedLine<P>, E>
) : GraphData<StandardUndirectedGraphDataWT, P, V, E> {

    companion object {
        enum class StandardUndirectedGraphDataWT

        fun <P, V, E> narrow(
            container: GraphData<StandardUndirectedGraphDataWT, P, V, E>
        ): StandardUndirectedGraphData<P, V, E> {
            return container as StandardUndirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<StandardUndirectedGraphDataWT, P, V, E>.narrowed():
            StandardUndirectedGraphData<P, V, E> {
            return StandardUndirectedGraphData.narrow(this)
        }

        private val EMPTY: StandardUndirectedGraphData<Any, Any, Any> =
            StandardUndirectedGraphData<Any, Any, Any>(persistentMapOf(), persistentMapOf())

        fun <P, V, E> empty(): StandardUndirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as StandardUndirectedGraphData<P, V, E>
        }
    }
}
