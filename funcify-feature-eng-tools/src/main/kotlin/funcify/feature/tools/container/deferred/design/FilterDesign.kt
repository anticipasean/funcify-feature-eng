package funcify.feature.tools.container.deferred.design

import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.template.DeferredTemplate

internal class FilterDesign<SWT, I>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val condition: (I) -> Boolean,
    val ifConditionUnmet: (I) -> Throwable
) : DeferredDesign<SWT, I> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
        return template.filter(condition, ifConditionUnmet, currentDesign.fold(template))
    }
}
