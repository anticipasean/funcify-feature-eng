package funcify.feature.tools.container.deferred

import reactor.core.publisher.Mono

internal class FlatMapMonoDesign<SWT, I, O>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val mapper: (I) -> Mono<out O>
) : DeferredDesign<SWT, O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        return template.flatMapMono(mapper, currentDesign.fold(template))
    }
}
