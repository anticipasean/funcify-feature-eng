package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.UndirectedGraphData.Companion.UndirectedGraphDataWT
import funcify.feature.graph.design.PersistentGraphDesign

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class UndirectedPersistentGraphContext<P, V, E>(
    override val behavior: GraphBehavior<UndirectedGraphDataWT>,
    override val data: GraphData<UndirectedGraphDataWT, P, V, E>
) : PersistentGraphDesign<UndirectedGraphDataWT, P, V, E> {}
