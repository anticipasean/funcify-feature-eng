package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal interface TwoToOnePathToEdgePersistentGraphTemplate :
    PersistentGraphTemplate<TwoToOnePathToEdgeGraphWT> {

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = edgesByPathPair
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair =
                edgesSetByPathPair
                    .asIterable()
                    .flatMap { e -> e.value.asIterable().map { edge -> e.key to edge } }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { pm, e -> pm.put(e.first, e.second) }
        )
    }
}
