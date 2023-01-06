package funcify.feature.graph.behavior

internal object GraphBehaviorFactory {

    /**
     * The _initial_ behaviors of the [DirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialDirectedGraphTemplate: DirectedGraphBehavior by lazy {
        object : DirectedGraphBehavior {}
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
     * The _initial_ behaviors of the [UndirectedPersistentGraph]
     *
     * This template may be swapped out with a different one enabling different behavior during
     * processing
     *
     * NOTE: Failure to implement a method within a behavior will render this initial behavior
     * instance **uncompilable**, prompting compile-time errors
     */
    private val initialUndirectedGraphTemplate: UndirectedGraphBehavior by lazy {
        object : UndirectedGraphBehavior {}
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

    fun getDirectedGraphBehavior(): DirectedGraphBehavior {
        return initialDirectedGraphTemplate
    }

    fun getParallelizableEdgeDirectedGraphBehavior(): ParallelizableEdgeDirectedGraphBehavior {
        return initialParallelizableEdgeDirectedGraphTemplate
    }

    fun getUndirectedGraphBehavior(): UndirectedGraphBehavior {
        return initialUndirectedGraphTemplate
    }

    fun getParallelizableEdgeUndirectedGraphBehavior(): ParallelizableEdgeUndirectedGraphBehavior {
        return initialParallelizableEdgeUndirectedGraphBehavior
    }
}
