package funcify.feature.graph.source

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph.Companion.TwoToManyPathToEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import funcify.feature.graph.design.PersistentGraphDesign
import funcify.feature.graph.template.PersistentGraphTemplate
import funcify.feature.graph.template.TwoToManyPathToEdgePersistentGraphTemplate
import funcify.feature.graph.template.TwoToOnePathToEdgePersistentGraphTemplate
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal object PersistentGraphSourceContextFactory {

    val initialTwoToOnePathToEdgeGraphTemplate: TwoToOnePathToEdgePersistentGraphTemplate by lazy {
        object : TwoToOnePathToEdgePersistentGraphTemplate {}
    }

    val initialTwoToManyPathToEdgeGraphTemplate:
        TwoToManyPathToEdgePersistentGraphTemplate by lazy {
        object : TwoToManyPathToEdgePersistentGraphTemplate {}
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

    internal class TwoToManyPathToEdgePersistentGraphSourceDesign<P, V, E>(
        val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
        val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> = persistentMapOf(),
        override val template: PersistentGraphTemplate<TwoToManyPathToEdgeGraphWT> =
            initialTwoToManyPathToEdgeGraphTemplate
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
