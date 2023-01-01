package funcify.feature.graph.source

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeGraph.Companion.ParallelizableEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import funcify.feature.graph.design.PersistentGraphDesign
import funcify.feature.graph.template.ParallelizableEdgeGraphTemplate
import funcify.feature.graph.template.PersistentGraphTemplate
import funcify.feature.graph.template.TwoToOnePathToEdgePersistentGraphTemplate
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal object PersistentGraphSourceContextFactory {

    val initialTwoToOnePathToEdgeGraphTemplate: TwoToOnePathToEdgePersistentGraphTemplate by lazy {
        object : TwoToOnePathToEdgePersistentGraphTemplate {}
    }

    val initialParallelizableEdgeGraphTemplate: ParallelizableEdgeGraphTemplate by lazy {
        object : ParallelizableEdgeGraphTemplate {}
    }

    internal class TwoToOnePathToEdgePersistentGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
        val edgesByPathPair: PersistentMap<Pair<P, P>, E> = persistentMapOf(),
        override val template: PersistentGraphTemplate<TwoToOnePathToEdgeGraphWT> =
            initialTwoToOnePathToEdgeGraphTemplate
    ) : PersistentGraphDesign<TwoToOnePathToEdgeGraphWT, P, V, E> {

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
        override val template: PersistentGraphTemplate<ParallelizableEdgeGraphWT> =
            initialParallelizableEdgeGraphTemplate
    ) : PersistentGraphDesign<ParallelizableEdgeGraphWT, P, V, E> {

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
