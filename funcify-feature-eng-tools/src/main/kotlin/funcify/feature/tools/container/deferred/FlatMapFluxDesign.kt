package funcify.feature.tools.container.deferred

import reactor.core.publisher.Flux

internal class FlatMapFluxDesign<SWT, I, O>(
    override val template: DeferredTemplate<SWT>,
    val fluxDesign: DeferredDesign<SWT, I>,
    val mapper: (I) -> Flux<out O>
) : DeferredDesign<SWT, O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        return template.flatMapFlux(mapper, fluxDesign.fold(template))
    }
}
