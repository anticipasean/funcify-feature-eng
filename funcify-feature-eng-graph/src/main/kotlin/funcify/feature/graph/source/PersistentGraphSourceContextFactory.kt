package funcify.feature.graph.source

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph.Companion.TwoToManyPathToEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import funcify.feature.graph.design.PersistentGraphDesign
import funcify.feature.graph.template.PersistentGraphTemplate
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal object PersistentGraphSourceContextFactory {

    internal class TwoToOnePathToEdgePersistentGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesByPathPair: PersistentMap<Pair<P, P>, E>,
        override val template: PersistentGraphTemplate<TwoToOnePathToEdgeGraphWT>
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

    internal class TwoToManyPathToEdgePersistentGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V>,
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>,
        override val template: PersistentGraphTemplate<TwoToManyPathToEdgeGraphWT>
    ) : PersistentGraphDesign<TwoToManyPathToEdgeGraphWT, P, V, E> {

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
