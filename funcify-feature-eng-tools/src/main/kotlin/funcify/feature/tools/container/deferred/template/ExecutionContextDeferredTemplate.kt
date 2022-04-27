package funcify.feature.tools.container.deferred.template

import funcify.feature.tools.container.deferred.container.DeferredContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

internal interface ExecutionContextDeferredTemplate<WT> : DeferredTemplate<WT> {

    fun <I, O> map(
        executor: Executor,
        mapper: (I) -> O,
        container: DeferredContainer<WT, I>,
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapMono(
        executor: Executor,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapFlux(
        executor: Executor,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> map(
        scheduler: Scheduler,
        mapper: (I) -> O,
        container: DeferredContainer<WT, I>,
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapMono(
        scheduler: Scheduler,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, O> flatMapFlux(
        scheduler: Scheduler,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>
}
