package funcify.feature.tools.container.deferred.design

import arrow.core.Option
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.template.DeferredTemplate

internal class FilterOptionDesign<SWT, I>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val condition: (I) -> Boolean
) : DeferredDesign<SWT, Option<I>> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, Option<I>> {
        return template.filter(condition, currentDesign.fold(template))
    }
}
