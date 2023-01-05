package funcify.feature.graph.design

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.context.DirectedPersistentGraphContext
import funcify.feature.graph.context.ParallelizableEdgeGraphContext
import funcify.feature.graph.data.DirectedGraphData
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * The **design** of a graph includes both its data/contents [GraphData] and its behavior
 * [GraphBehavior]
 *
 * Implementations of a graph **design** are **contexts** e.g. [DirectedPersistentGraphContext]
 */
internal interface PersistentGraphDesign<CWT, P, V, E> : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    val behavior: GraphBehavior<CWT>

    val data: GraphData<CWT, P, V, E>

}
