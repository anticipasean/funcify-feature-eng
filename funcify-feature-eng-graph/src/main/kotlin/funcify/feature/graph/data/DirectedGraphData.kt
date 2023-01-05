package funcify.feature.graph.data

import funcify.feature.graph.data.DirectedGraphData.Companion.DirectedGraphWT
import kotlinx.collections.immutable.PersistentMap

internal data class DirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesByPointPair: PersistentMap<Pair<P, P>, E>
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
    }
}
