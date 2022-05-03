package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph.Companion.TwoToManyPathToEdgeGraphWT
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal interface TwoToManyPathToEdgePersistentGraphTemplate :
    PersistentGraphTemplate<TwoToManyPathToEdgeGraphWT> {

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<TwoToManyPathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair =
                edgesByPathPair.asIterable().fold(
                        persistentMapOf<Pair<P, P>, PersistentSet<E>>()
                    ) { pm, e -> pm.put(e.key, persistentSetOf(e.value)) }
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<TwoToManyPathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgesSetByPathPair
        )
    }
}
