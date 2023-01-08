package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.behavior.UndirectedGraphBehavior
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardUndirectedGraphData.Companion.StandardUndirectedGraphDataWT
import funcify.feature.graph.design.UndirectedPersistentGraphDesign

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class StandardUndirectedPersistentGraphContext<P, V, E>(
    override val behavior: UndirectedGraphBehavior<StandardUndirectedGraphDataWT>,
    override val data: GraphData<StandardUndirectedGraphDataWT, P, V, E> =
        GraphBehaviorFactory.getStandardUndirectedGraphBehavior().empty()
) : UndirectedPersistentGraphDesign<StandardUndirectedGraphDataWT, P, V, E> {

    override fun <P, V, E> unit(
        data: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
    ): StandardUndirectedPersistentGraphContext<P, V, E> {
        return StandardUndirectedPersistentGraphContext(behavior, data)
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
