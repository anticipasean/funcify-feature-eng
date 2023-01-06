package funcify.feature.graph.behavior

internal object GraphBehaviorFactory {

    /**
     * The _initial_ behaviors of the [StandardDirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialStandardDirectedGraphTemplate: StandardDirectedGraphBehavior by lazy {
        object : StandardDirectedGraphBehavior {}
    }
    /**
     * The _initial_ behaviors of the [ParallelizableEdgeDirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialParallelizableEdgeDirectedGraphTemplate:
        ParallelizableEdgeDirectedGraphBehavior by lazy {
        object : ParallelizableEdgeDirectedGraphBehavior {}
    }

    /**
     * The _initial_ behaviors of the [StandardUndirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialStandardUndirectedGraphTemplate: StandardUndirectedGraphBehavior by lazy {
        object : StandardUndirectedGraphBehavior {}
    }

    /**
     * The _initial_ behaviors of the [ParallelizableEdgeUndirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialParallelizableEdgeUndirectedGraphBehavior:
        ParallelizableEdgeUndirectedGraphBehavior by lazy {
        object : ParallelizableEdgeUndirectedGraphBehavior {}
    }

    fun getStandardDirectedGraphBehavior(): StandardDirectedGraphBehavior {
        return initialStandardDirectedGraphTemplate
    }

    fun getParallelizableEdgeDirectedGraphBehavior(): ParallelizableEdgeDirectedGraphBehavior {
        return initialParallelizableEdgeDirectedGraphTemplate
    }

    fun getStandardUndirectedGraphBehavior(): StandardUndirectedGraphBehavior {
        return initialStandardUndirectedGraphTemplate
    }

    fun getParallelizableEdgeUndirectedGraphBehavior(): ParallelizableEdgeUndirectedGraphBehavior {
        return initialParallelizableEdgeUndirectedGraphBehavior
    }
}
