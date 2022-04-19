package funcify.feature.tools.container.deferred

import java.util.concurrent.CompletionStage

internal class FlatMapCompletionStageDesign<SWT, I, O>(
    override val template: DeferredTemplate<SWT>,
    val currentDesign: DeferredDesign<SWT, I>,
    val mapper: (I) -> CompletionStage<out O>
) : DeferredDesign<SWT, O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        return template.flatMapCompletionStage(mapper, currentDesign.fold(template))
    }
}
