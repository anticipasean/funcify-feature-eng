package funcify.feature.graph.builder

import funcify.feature.graph.GraphBuilder
import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehaviorFactory
import funcify.feature.graph.context.ParallelizableEdgeUndirectedPersistentGraphContext
import funcify.feature.graph.context.StandardUndirectedPersistentGraphContext

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
            sequenceOf(GraphDescriptor.PERMIT_PARALLEL_EDGES, GraphDescriptor.PERMIT_SELF_LOOPS)
                .all { gd -> gd in graphDescriptors } -> {
                ParallelizableEdgeUndirectedPersistentGraphContext<P, V, E>(
                    behavior =
                        GraphBehaviorFactory
                            .getSelfLoopingParallelizableEdgeUndirectedGraphBehavior()
                )
            }
            GraphDescriptor.PERMIT_PARALLEL_EDGES in graphDescriptors -> {
                ParallelizableEdgeUndirectedPersistentGraphContext<P, V, E>(
                    behavior = GraphBehaviorFactory.getParallelizableEdgeUndirectedGraphBehavior()
                )
            }
            GraphDescriptor.PERMIT_SELF_LOOPS in graphDescriptors -> {
                StandardUndirectedPersistentGraphContext<P, V, E>(
                    behavior = GraphBehaviorFactory.getSelfLoopingUndirectedGraphBehavior()
                )
            }
            else -> {
                StandardUndirectedPersistentGraphContext<P, V, E>(
                    behavior = GraphBehaviorFactory.getStandardUndirectedGraphBehavior()
                )
            }
        }
    }
}
