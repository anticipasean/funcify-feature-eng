package funcify.feature.tools.container.deferred

internal class FilterDesign<SWT, I>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val condition: (I) -> Boolean
) : DeferredDesign<SWT, I> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
        return template.filter(condition, currentDesign.fold(template))
    }
}
