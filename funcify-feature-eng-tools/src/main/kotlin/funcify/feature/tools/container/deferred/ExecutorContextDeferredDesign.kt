package funcify.feature.tools.container.deferred

import java.util.concurrent.Executor

internal interface ExecutorContextDeferredDesign<SWT, I> : DeferredDesign<SWT, I> {

    val executor: Executor
}
