package funcify.feature.graph.data

import funcify.feature.graph.data.UndirectedGraphData.Companion.UndirectedGraphDataWT

internal data class UndirectedGraphData<P, V, E>(

                                                ) : GraphData<UndirectedGraphDataWT, P, V, E> {

    companion object {
        enum class UndirectedGraphDataWT
    }

}
