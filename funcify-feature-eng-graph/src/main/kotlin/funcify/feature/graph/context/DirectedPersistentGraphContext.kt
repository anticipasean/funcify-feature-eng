package funcify.feature.graph.context

import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.data.DirectedGraphData.Companion.DirectedGraphWT
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.design.DirectedPersistentGraphDesign
import kotlinx.collections.immutable.persistentMapOf

internal class DirectedPersistentGraphContext<P, V, E>(
    override val behavior: GraphBehavior<DirectedGraphWT> =
        GraphBehaviorFactory.getDirectedGraphBehavior(),
    override val data: GraphData<DirectedGraphWT, P, V, E> =
        GraphBehaviorFactory.getDirectedGraphBehavior()
            .fromVerticesAndEdges(persistentMapOf(), persistentMapOf())
) : DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E> {

    companion object {

        fun <P, V, E> narrow(
            design: DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E>
        ): DirectedPersistentGraphContext<P, V, E> {
            return design as DirectedPersistentGraphContext<P, V, E>
        }

        fun <P, V, E> DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E>.narrowed():
            DirectedPersistentGraphContext<P, V, E> {
            return DirectedPersistentGraphContext.narrow(this)
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
