package funcify.feature.tools.container.deferred

internal class MapDesign<I, O>(val currentDesign: DeferredDesign<I>, val mapper: (I) -> O) :
    DeferredDesign<O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        val container: DeferredContainer<WT, I> = currentDesign.fold(template)
        return template.map(container, mapper)
    }
}
