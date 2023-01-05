package funcify.feature.graph.builder

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.GraphBuilder
import funcify.feature.graph.GraphBuilder.DirectedGraphBuilder
import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.context.DirectedPersistentGraphContext
import funcify.feature.graph.context.ParallelizableEdgeGraphContext

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class DefaultDirectedGraphBuilder<B : DirectedGraphBuilder<B>>(
    private val graphDescriptors: MutableSet<GraphDescriptor> = mutableSetOf()
) : DirectedGraphBuilder<B> {

    override fun <B : DirectedGraphBuilder<B>> directed(): DirectedGraphBuilder<B> {
        @Suppress("UNCHECKED_CAST") //
        return this as DirectedGraphBuilder<B>
    }

    private fun <B : DirectedGraphBuilder<B>> widen(): B {
        @Suppress("UNCHECKED_CAST") //
        return this as B
    }

    override fun <B : GraphBuilder.UndirectedGraphBuilder<B>> undirected():
        GraphBuilder.UndirectedGraphBuilder<B> {
        return DefaultUndirectedGraphBuilder(
            graphDescriptors.apply { remove(GraphDescriptor.DIRECTED) }
        )
    }

    override fun permitSelfLoops(): B {
        this.graphDescriptors.add(GraphDescriptor.PERMIT_SELF_LOOPS)
        return widen()
    }

    override fun permitParallelEdges(): B {
        this.graphDescriptors.add(GraphDescriptor.PERMIT_PARALLEL_EDGES)
        return widen()
    }

    override fun <P, V, E> build(): DirectedPersistentGraph<P, V, E> {
        return when {
            graphDescriptors.contains(GraphDescriptor.PERMIT_PARALLEL_EDGES) -> {
                ParallelizableEdgeGraphContext()
            }
            else -> {
                DirectedPersistentGraphContext()
            }
        }
    }
}
