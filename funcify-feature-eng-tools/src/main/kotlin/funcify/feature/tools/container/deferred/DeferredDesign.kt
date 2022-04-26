package funcify.feature.tools.container.deferred

import java.util.concurrent.CompletionStage

internal interface DeferredDesign<I> : Deferred<I> {

    override fun <O> map(mapper: (I) -> O): Deferred<O> {
        return MapDesign<I, O>(currentDesign = this, mapper = mapper)
    }

    override fun <O> flatMapCompletionStage(mapper: (I) -> CompletionStage<out O>): Deferred<O> {
        return FlatMapCompletionStageDesign<I, O>(currentDesign = this, mapper = mapper)
    }

    fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I>
}
