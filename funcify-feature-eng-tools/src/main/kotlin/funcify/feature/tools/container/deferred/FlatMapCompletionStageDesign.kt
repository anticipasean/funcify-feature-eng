package funcify.feature.tools.container.deferred

import java.util.concurrent.CompletionStage

internal class FlatMapCompletionStageDesign<I, O>(
    val currentDesign: DeferredDesign<I>,
    val mapper: (I) -> CompletionStage<out O>
) : DeferredDesign<O> {

    override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, O> {
        val deferredContainer: DeferredContainer<WT, I> = currentDesign.fold(template)
        return template.flatMapCompletionStage(deferredContainer, mapper)
    }
}
