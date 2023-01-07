package funcify.feature.graph.context

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.behavior.DirectedGraphBehavior
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.design.DirectedPersistentGraphDesign
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal class ParallelizableEdgeDirectedGraphContext<P, V, E>(
    override val behavior: DirectedGraphBehavior<ParallelizableEdgeDirectedGraphWT> =
        GraphBehaviorFactory.getParallelizableEdgeDirectedGraphBehavior(),
    override val data: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getParallelizableEdgeDirectedGraphBehavior().empty()
) : DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E> {

    override fun <P, V, E> unit(
        data: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
    ): ParallelizableEdgeDirectedGraphContext<P, V, E> {
        return ParallelizableEdgeDirectedGraphContext<P, V, E>(behavior, data)
    }

    /** lazily calculates the string representation for the materialized container */
    private val stringRepresentation: String by lazy { stringify() }

    override fun toString(): String {
        return stringRepresentation
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf(GraphDescriptor.DIRECTED, GraphDescriptor.PERMIT_PARALLEL_EDGES)
    }

    override fun equals(other: Any?): Boolean {
        return this.data.equals(other)
    }

    override fun hashCode(): Int {
        return this.data.hashCode()
    }
}
