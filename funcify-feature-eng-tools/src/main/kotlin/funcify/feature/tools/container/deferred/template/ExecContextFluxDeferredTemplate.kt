package funcify.feature.tools.container.deferred.template

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

internal interface ExecContextFluxDeferredTemplate :
    ExecutionContextDeferredTemplate<FluxDeferredContainerWT>, FluxDeferredTemplate {

    override fun <I, O> map(
        executor: Executor,
        mapper: (I) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container
                .narrowed()
                .flux
                .publishOn(Schedulers.fromExecutor(executor))
                .mapNotNull(mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        executor: Executor,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(Schedulers.fromExecutor(executor)).flatMap { i: I ->
                mapper.invoke(i).toMono()
            }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(Schedulers.fromExecutor(executor)).flatMap { i: I ->
                Mono.fromCompletionStage(mapper.invoke(i)).flux()
            }
        )
    }

    override fun <I, O> flatMapMono(
        executor: Executor,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(Schedulers.fromExecutor(executor)).flatMap(mapper)
        )
    }

    override fun <I, O> flatMapFlux(
        executor: Executor,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(Schedulers.fromExecutor(executor)).flatMap(mapper)
        )
    }

    override fun <I, O> map(
        scheduler: Scheduler,
        mapper: (I) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(scheduler).mapNotNull(mapper)
        )
    }

    override fun <I, O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(scheduler).flatMap { i: I ->
                Mono.fromCompletionStage(mapper.invoke(i))
            }
        )
    }

    override fun <I, O> flatMapKFuture(
        scheduler: Scheduler,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(scheduler).flatMap { i: I ->
                mapper.invoke(i).toMono()
            }
        )
    }

    override fun <I, O> flatMapMono(
        scheduler: Scheduler,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(scheduler).flatMap(mapper)
        )
    }

    override fun <I, O> flatMapFlux(
        scheduler: Scheduler,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.publishOn(scheduler).flatMap(mapper)
        )
    }
}
