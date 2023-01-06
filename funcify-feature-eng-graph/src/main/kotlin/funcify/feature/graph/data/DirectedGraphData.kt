package funcify.feature.graph.data

import funcify.feature.graph.data.DirectedGraphData.Companion.DirectedGraphWT
import funcify.feature.graph.line.DirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesByLine: PersistentMap<DirectedLine<P>, E>
) : GraphData<DirectedGraphWT, P, V, E> {

    companion object {
        enum class DirectedGraphWT

        fun <P, V, E> narrow(
            container: GraphData<DirectedGraphWT, P, V, E>
        ): DirectedGraphData<P, V, E> {
            return container as DirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<DirectedGraphWT, P, V, E>.narrowed(): DirectedGraphData<P, V, E> {
            return DirectedGraphData.narrow(this)
        }

        private val EMPTY: DirectedGraphData<Any, Any, Any> =
            DirectedGraphData<Any, Any, Any>(persistentMapOf(), persistentMapOf())

        fun <P, V, E> empty(): DirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as DirectedGraphData<P, V, E>
        }
    }
}
