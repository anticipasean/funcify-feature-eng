package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.narrowed
import java.util.concurrent.CompletionStage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface FluxDeferredTemplate : DeferredTemplate<FluxDeferredContainerWT> {
    override fun <I> fromKFuture(
        kFuture: KFuture<I>
    ): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(kFuture.toMono().flux())
    }

    override fun <I> fromMono(mono: Mono<I>): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(mono.flux())
    }

    override fun <I> fromFlux(flux: Flux<I>): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(flux)
    }

    override fun <I, O> map(
        mapper: (I) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(container.narrowed().flux.map(mapper))
    }

    override fun <I, O> flatMapCompletionStage(
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap { i: I -> Mono.fromCompletionStage(mapper.invoke(i)) }
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.filter(condition)
        )
    }
}
