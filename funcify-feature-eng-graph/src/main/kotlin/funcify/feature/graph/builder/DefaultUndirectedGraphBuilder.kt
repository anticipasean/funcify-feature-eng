package funcify.feature.graph.builder

import funcify.feature.graph.GraphBuilder
import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.context.ParallelizableEdgeUndirectedPersistentGraphContext
import funcify.feature.graph.context.UndirectedPersistentGraphContext

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class DefaultUndirectedGraphBuilder<B : GraphBuilder.UndirectedGraphBuilder<B>>(
    private val graphDescriptors: MutableSet<GraphDescriptor> = mutableSetOf()
) : GraphBuilder.UndirectedGraphBuilder<B> {

    override fun <B : GraphBuilder.DirectedGraphBuilder<B>> directed():
        GraphBuilder.DirectedGraphBuilder<B> {
        return DefaultDirectedGraphBuilder(graphDescriptors.apply { add(GraphDescriptor.DIRECTED) })
    }

    override fun <B : GraphBuilder.UndirectedGraphBuilder<B>> undirected():
        GraphBuilder.UndirectedGraphBuilder<B> {
        @Suppress("UNCHECKED_CAST") //
        return this as GraphBuilder.UndirectedGraphBuilder<B>
    }

    override fun permitSelfLoops(): B {
        graphDescriptors.add(GraphDescriptor.PERMIT_SELF_LOOPS)
        return widen()
    }

    private fun <B : GraphBuilder.UndirectedGraphBuilder<B>> widen(): B {
        @Suppress("UNCHECKED_CAST") //
        return this as B
    }

    override fun permitParallelEdges(): B {
        graphDescriptors.add(GraphDescriptor.PERMIT_PARALLEL_EDGES)
        return widen()
    }

    override fun <P, V, E> build(): PersistentGraph<P, V, E> {
        return when {
            graphDescriptors.contains(GraphDescriptor.PERMIT_PARALLEL_EDGES) -> {
                ParallelizableEdgeUndirectedPersistentGraphContext<P, V, E>()
            }
            else -> {
                UndirectedPersistentGraphContext<P, V, E>()
            }
        }
    }
}
