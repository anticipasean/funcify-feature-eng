package funcify.feature.tools.container.deferred

import arrow.core.Option

internal class FilterOptionDesign<SWT, I>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val condition: (I) -> Boolean
) : DeferredDesign<SWT, Option<I>> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, Option<I>> {
        return template.filter(condition, currentDesign.fold(template))
    }
}
