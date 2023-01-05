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

    fun getDirectedGraphBehavior(): DirectedGraphBehavior {
        return initialDirectedGraphTemplate
    }

    fun getParallelizableEdgeDirectedGraphBehavior(): ParallelizableEdgeDirectedGraphBehavior {
        return initialParallelizableEdgeDirectedGraphTemplate
    }
}
