package funcify.feature.graph.context

import funcify.feature.graph.behavior.DirectedGraphBehavior
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.StandardDirectedGraphWT
import funcify.feature.graph.design.DirectedPersistentGraphDesign

internal class DirectedPersistentGraphContext<P, V, E>(
    override val behavior: DirectedGraphBehavior<StandardDirectedGraphWT> =
        GraphBehaviorFactory.getStandardDirectedGraphBehavior(),
    override val data: GraphData<StandardDirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getStandardDirectedGraphBehavior().empty()
) : DirectedPersistentGraphDesign<StandardDirectedGraphWT, P, V, E> {

    override fun <P, V, E> unit(
        data: GraphData<StandardDirectedGraphWT, P, V, E>,
    ): DirectedPersistentGraphContext<P, V, E> {
        return DirectedPersistentGraphContext<P, V, E>(behavior, data)
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
