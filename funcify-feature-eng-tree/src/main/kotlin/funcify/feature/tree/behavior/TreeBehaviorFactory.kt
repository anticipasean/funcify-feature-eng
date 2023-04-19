package funcify.feature.tree.behavior

internal object TreeBehaviorFactory {

    private val initialStandardObjectBranchBehavior by lazy {
        object : StandardObjectBranchBehavior {}
    }
    private val initialStandardArrayBranchBehavior by lazy {
        object : StandardArrayBranchBehavior {}
    }
    private val initialStandardLeafBehavior by lazy { object : StandardLeafBehavior {} }

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
