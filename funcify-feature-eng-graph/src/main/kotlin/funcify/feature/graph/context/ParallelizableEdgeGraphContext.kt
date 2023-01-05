package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.design.DirectedPersistentGraphDesign
import kotlinx.collections.immutable.persistentMapOf

internal class ParallelizableEdgeGraphContext<P, V, E>(
    override val behavior: GraphBehavior<ParallelizableEdgeDirectedGraphWT> =
        GraphBehaviorFactory.getParallelizableEdgeDirectedGraphBehavior(),
    override val data: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getParallelizableEdgeDirectedGraphBehavior()
            .fromVerticesAndEdgeSets(persistentMapOf(), persistentMapOf())
) : DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E> {

    companion object {

        fun <P, V, E> narrow(
            design: DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E>
        ): ParallelizableEdgeGraphContext<P, V, E> {
            return design as ParallelizableEdgeGraphContext<P, V, E>
        }

        fun <P, V, E> DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E>
            .narrowed(): ParallelizableEdgeGraphContext<P, V, E> {
            return ParallelizableEdgeGraphContext.narrow(this)
        }
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
