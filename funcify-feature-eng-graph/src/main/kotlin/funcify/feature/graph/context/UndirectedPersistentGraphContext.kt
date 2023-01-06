package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.UndirectedGraphData.Companion.UndirectedGraphDataWT
import funcify.feature.graph.design.UndirectedPersistentGraphDesign

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class UndirectedPersistentGraphContext<P, V, E>(
    override val behavior: GraphBehavior<UndirectedGraphDataWT> =
        GraphBehaviorFactory.getUndirectedGraphBehavior(),
    override val data: GraphData<UndirectedGraphDataWT, P, V, E> =
        GraphBehaviorFactory.getUndirectedGraphBehavior().empty()
) : UndirectedPersistentGraphDesign<UndirectedGraphDataWT, P, V, E> {

    override fun <P, V, E> unit(
        behavior: GraphBehavior<UndirectedGraphDataWT>,
        data: GraphData<UndirectedGraphDataWT, P, V, E>,
    ): UndirectedPersistentGraphContext<P, V, E> {
        return UndirectedPersistentGraphContext(behavior, data)
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
