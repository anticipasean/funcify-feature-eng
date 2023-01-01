package funcify.feature.graph.source

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.DirectedGraph.Companion.DirectedGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.design.PersistentGraphDesign
import funcify.feature.graph.template.DirectedGraphTemplate
import funcify.feature.graph.template.ParallelizableEdgeDirectedGraphTemplate
import funcify.feature.graph.template.PersistentGraphTemplate
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal object PersistentGraphSourceContextFactory {

    val initialDirectedGraphTemplate: DirectedGraphTemplate by lazy {
        object : DirectedGraphTemplate {}
    }

    val initialParallelizableEdgeDirectedGraphTemplate:
        ParallelizableEdgeDirectedGraphTemplate by lazy {
        object : ParallelizableEdgeDirectedGraphTemplate {}
    }

    internal class DirectedPersistentGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
        val edgesByPathPair: PersistentMap<Pair<P, P>, E> = persistentMapOf(),
        override val template: PersistentGraphTemplate<DirectedGraphWT> =
            initialDirectedGraphTemplate
    ) : PersistentGraphDesign<DirectedGraphWT, P, V, E> {

        override fun <WT> fold(
            template: PersistentGraphTemplate<WT>
        ): PersistentGraphContainer<WT, P, V, E> {
            return template.fromVerticesAndEdges(
                verticesByPath = verticesByPath,
                edgesByPathPair = edgesByPathPair
            )
        }
    }

    internal class ParallelizableEdgeGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> = persistentMapOf(),
        override val template: PersistentGraphTemplate<ParallelizableEdgeDirectedGraphWT> =
            initialParallelizableEdgeDirectedGraphTemplate
    ) : PersistentGraphDesign<ParallelizableEdgeDirectedGraphWT, P, V, E> {

        override fun <WT> fold(
            template: PersistentGraphTemplate<WT>
        ): PersistentGraphContainer<WT, P, V, E> {
            return template.fromVerticesAndEdgeSets(
                verticesByPath = verticesByPath,
                edgesSetByPathPair = edgesSetByPathPair
            )
        }
    }
}
