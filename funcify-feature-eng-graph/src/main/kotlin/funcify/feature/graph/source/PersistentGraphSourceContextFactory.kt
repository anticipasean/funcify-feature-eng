package funcify.feature.graph.source

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.DirectedGraph.Companion.DirectedGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.design.DirectedPersistentGraphDesign
import funcify.feature.graph.template.DirectedGraphTemplate
import funcify.feature.graph.template.ParallelizableEdgeDirectedGraphTemplate
import funcify.feature.graph.template.PersistentGraphTemplate
import kotlinx.collections.immutable.persistentMapOf

internal object PersistentGraphSourceContextFactory {

    /**
     * The _initial_ behaviors of the [DirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     */
    val initialDirectedGraphTemplate: DirectedGraphTemplate by lazy {
        object : DirectedGraphTemplate {}
    }
    /**
     * The _initial_ behaviors of the [ParallelizableEdgeDirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     */
    val initialParallelizableEdgeDirectedGraphTemplate:
        ParallelizableEdgeDirectedGraphTemplate by lazy {
        object : ParallelizableEdgeDirectedGraphTemplate {}
    }

    fun <P, V, E> DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E>.narrowed():
        DirectedPersistentGraphSourceDesign<P, V, E> {
        return DirectedPersistentGraphSourceDesign.narrow(this)
    }

    fun <P, V, E> DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E>
        .narrowed(): ParallelizableEdgeGraphSourceDesign<P, V, E> {
        return ParallelizableEdgeGraphSourceDesign.narrow(this)
    }

    internal class DirectedPersistentGraphSourceDesign<P, V, E>(
        override val template: PersistentGraphTemplate<DirectedGraphWT> =
            initialDirectedGraphTemplate,
        override val materializedContainer: PersistentGraphContainer<DirectedGraphWT, P, V, E> =
            initialDirectedGraphTemplate.fromVerticesAndEdges(persistentMapOf(), persistentMapOf())
    ) : DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E> {

        companion object {

            fun <P, V, E> narrow(
                design: DirectedPersistentGraphDesign<DirectedGraphWT, P, V, E>
            ): DirectedPersistentGraphSourceDesign<P, V, E> {
                return design as DirectedPersistentGraphSourceDesign<P, V, E>
            }
        }

        /** lazily calculates the string representation for the materialized container */
        private val stringRepresentation: String by lazy { stringify() }

        override fun toString(): String {
            return stringRepresentation
        }
    }

    internal class ParallelizableEdgeGraphSourceDesign<P, V, E>(
        override val template: PersistentGraphTemplate<ParallelizableEdgeDirectedGraphWT> =
            initialParallelizableEdgeDirectedGraphTemplate,
        override val materializedContainer:
            PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> =
            initialParallelizableEdgeDirectedGraphTemplate.fromVerticesAndEdgeSets(
                persistentMapOf(),
                persistentMapOf()
            )
    ) : DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E> {

        companion object {

            fun <P, V, E> narrow(
                design: DirectedPersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E>
            ): ParallelizableEdgeGraphSourceDesign<P, V, E> {
                return design as ParallelizableEdgeGraphSourceDesign<P, V, E>
            }
        }

        /** lazily calculates the string representation for the materialized container */
        private val stringRepresentation: String by lazy { stringify() }

        override fun toString(): String {
            return stringRepresentation
        }
    }
}
