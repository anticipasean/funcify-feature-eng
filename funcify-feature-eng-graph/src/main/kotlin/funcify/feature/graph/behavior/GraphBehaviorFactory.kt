package funcify.feature.graph.behavior

internal object GraphBehaviorFactory {

    private val initialStandardDirectedGraphBehavior: StandardDirectedGraphBehavior by lazy {
        object : StandardDirectedGraphBehavior {}
    }

    private val initialParallelizableEdgeDirectedGraphBehavior:
        ParallelizableEdgeDirectedGraphBehavior by lazy {
        object : ParallelizableEdgeDirectedGraphBehavior {}
    }

    private val initialStandardUndirectedGraphBehavior: StandardUndirectedGraphBehavior by lazy {
        object : StandardUndirectedGraphBehavior {}
    }

    private val initialParallelizableEdgeUndirectedGraphBehavior:
        ParallelizableEdgeUndirectedGraphBehavior by lazy {
        object : ParallelizableEdgeUndirectedGraphBehavior {}
    }

    private val initialSelfLoopingDirectedGraphBehavior: SelfLoopingDirectedGraphBehavior by lazy {
        object : SelfLoopingDirectedGraphBehavior {}
    }

    private val initialSelfLoopingParallelizableEdgeDirectedGraphBehavior:
        SelfLoopingParallelizableEdgeDirectedGraphBehavior by lazy {
        object : SelfLoopingParallelizableEdgeDirectedGraphBehavior {}
    }

    private val initialSelfLoopingUndirectedGraphBehavior:
        SelfLoopingUndirectedGraphBehavior by lazy {
        object : SelfLoopingUndirectedGraphBehavior {}
    }

    private val initialSelfLoopingParallelizableEdgeUndirectedGraphBehavior:
        SelfLoopingParallelizableEdgeUndirectedGraphBehavior by lazy {
        object : SelfLoopingParallelizableEdgeUndirectedGraphBehavior {}
    }

    fun getStandardDirectedGraphBehavior(): StandardDirectedGraphBehavior {
        return initialStandardDirectedGraphBehavior
    }

    fun getParallelizableEdgeDirectedGraphBehavior(): ParallelizableEdgeDirectedGraphBehavior {
        return initialParallelizableEdgeDirectedGraphBehavior
    }

    fun getStandardUndirectedGraphBehavior(): StandardUndirectedGraphBehavior {
        return initialStandardUndirectedGraphBehavior
    }

    fun getParallelizableEdgeUndirectedGraphBehavior(): ParallelizableEdgeUndirectedGraphBehavior {
        return initialParallelizableEdgeUndirectedGraphBehavior
    }

    fun getSelfLoopingDirectedGraphBehavior(): SelfLoopingDirectedGraphBehavior {
        return initialSelfLoopingDirectedGraphBehavior
    }

    fun getSelfLoopingParallelizableEdgeDirectedGraphBehavior():
        SelfLoopingParallelizableEdgeDirectedGraphBehavior {
        return initialSelfLoopingParallelizableEdgeDirectedGraphBehavior
    }

    fun getSelfLoopingUndirectedGraphBehavior(): SelfLoopingUndirectedGraphBehavior {
        return initialSelfLoopingUndirectedGraphBehavior
    }

    fun getSelfLoopingParallelizableEdgeUndirectedGraphBehavior():
        SelfLoopingParallelizableEdgeUndirectedGraphBehavior {
        return initialSelfLoopingParallelizableEdgeUndirectedGraphBehavior
    }
}
