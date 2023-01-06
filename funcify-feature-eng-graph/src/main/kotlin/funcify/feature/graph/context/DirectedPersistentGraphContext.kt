package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.DirectedGraphData.Companion.DirectedGraphWT
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.design.DirectedPersistentGraphDesign

internal class DirectedPersistentGraphContext<P, V, E>(
    override val behavior: GraphBehavior<DirectedGraphWT> =
        GraphBehaviorFactory.getDirectedGraphBehavior(),
    override val data: GraphData<DirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getDirectedGraphBehavior().empty()
) : DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E> {

    override fun <P, V, E> unit(
        behavior: GraphBehavior<DirectedGraphWT>,
        data: GraphData<DirectedGraphWT, P, V, E>,
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
