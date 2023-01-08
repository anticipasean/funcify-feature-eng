package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData.Companion.ParallelizableEdgeUndirectedGraphWT
import funcify.feature.graph.design.UndirectedPersistentGraphDesign

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class ParallelizableEdgeUndirectedPersistentGraphContext<P, V, E>(
    override val behavior: GraphBehavior<ParallelizableEdgeUndirectedGraphWT>,
    override val data: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getParallelizableEdgeUndirectedGraphBehavior().empty()
) : UndirectedPersistentGraphDesign<ParallelizableEdgeUndirectedGraphWT, P, V, E> {

    override fun <P, V, E> unit(
        data: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
    ): ParallelizableEdgeUndirectedPersistentGraphContext<P, V, E> {
        return ParallelizableEdgeUndirectedPersistentGraphContext(behavior, data)
    }

    /** lazily calculates the string representation for the materialized container */
    private val stringRepresentation: String by lazy { stringify() }

    override fun toString(): String {
        return stringRepresentation
    }

    override fun equals(other: Any?): Boolean {
        return this.data.equals(other)
    }

    override fun hashCode(): Int {
        return this.data.hashCode()
    }
}
