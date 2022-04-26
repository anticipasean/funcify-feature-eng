package funcify.feature.tools.container.deferred

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture
import java.util.concurrent.CompletionStage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface DeferredTemplate<WT> {

    fun <I> fromKFuture(kFuture: KFuture<I>): DeferredContainer<WT, I>

    fun <I> fromMono(mono: Mono<I>): DeferredContainer<WT, I>

    fun <I> fromFlux(flux: Flux<I>): DeferredContainer<WT, I>
    fun <I, O> map(
        mapper: (I) -> O,
        container: DeferredContainer<WT, I>,
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapCompletionStage(
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapMono(
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapFlux(
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, Option<I>>

    fun <I> filter(
        condition: (I) -> Boolean,
        ifConditionUnmet: (I) -> Throwable,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, I>
}
