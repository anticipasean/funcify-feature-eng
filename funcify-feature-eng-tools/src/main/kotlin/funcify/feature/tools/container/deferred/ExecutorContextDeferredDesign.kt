package funcify.feature.tools.container.deferred

import java.util.concurrent.Executor

internal interface ExecutorContextDeferredDesign<I> : DeferredDesign<I> {

    val executor: Executor



}
