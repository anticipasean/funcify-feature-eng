package funcify.feature.tree.behavior

internal object TreeBehaviorFactory {

    private val initialStandardEmptyTreeBehavior by lazy { object : StandardEmptyTreeBehavior {} }
    private val initialStandardObjectBranchBehavior by lazy {
        object : StandardObjectBranchBehavior {}
    }
    private val initialStandardArrayBranchBehavior by lazy {
        object : StandardArrayBranchBehavior {}
    }
    private val initialStandardLeafBehavior by lazy { object : StandardLeafBehavior {} }

    fun getStandardEmptyTreeBehavior(): StandardEmptyTreeBehavior {
        return initialStandardEmptyTreeBehavior
    }

    fun getStandardObjectBranchBehavior(): StandardObjectBranchBehavior {
        return initialStandardObjectBranchBehavior
    }

    fun getStandardArrayBranchBehavior(): StandardArrayBranchBehavior {
        return initialStandardArrayBranchBehavior
    }

    fun getStandardLeafBehavior(): StandardLeafBehavior {
        return initialStandardLeafBehavior
    }
}
