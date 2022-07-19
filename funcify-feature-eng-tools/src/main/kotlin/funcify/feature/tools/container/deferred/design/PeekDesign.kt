package funcify.feature.tools.container.deferred.design

import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.template.DeferredTemplate

internal class PeekDesign<SWT, I>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val ifSuccessFunction: (I) -> Unit,
    val ifFailureFunction: (Throwable) -> Unit
) : DeferredDesign<SWT, I> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
        return template.peek(ifSuccessFunction, ifFailureFunction, currentDesign.fold(template))
    }
}
