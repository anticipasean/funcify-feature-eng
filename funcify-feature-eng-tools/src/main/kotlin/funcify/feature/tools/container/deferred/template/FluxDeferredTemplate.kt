package funcify.feature.tools.container.deferred.template

import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
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
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.mapNotNull(mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap { i: I -> mapper.invoke(i).toMono() }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap { i: I -> Mono.fromCompletionStage(mapper.invoke(i)) }
        )
    }

    override fun <I, O> flatMapMono(
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap(mapper)
        )
    }

    override fun <I, O> flatMapFlux(
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap(mapper)
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, Option<I>> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.map { i: I ->
                if (condition.invoke(i)) {
                    i.toOption()
                } else {
                    None
                }
            }
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        ifConditionUnmet: (I) -> Throwable,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.flatMap { i: I ->
                if (condition.invoke(i)) {
                    Flux.just(i)
                } else {
                    Flux.error<I> { ifConditionUnmet.invoke(i) }
                }
            }
        )
    }

    override fun <I, I1, O> zip1(
        other: DeferredContainer<FluxDeferredContainerWT, I1>,
        combiner: (I, I1) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container.narrowed().flux.zipWith(other.narrowed().flux, combiner)
        )
    }

    override fun <I, I1, I2, O> zip2(
        other1: DeferredContainer<FluxDeferredContainerWT, I1>,
        other2: DeferredContainer<FluxDeferredContainerWT, I2>,
        combiner: (I, I1, I2) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container
                .narrowed()
                .flux
                .zipWith(other1.narrowed().flux)
                .zipWith(other2.narrowed().flux)
                .map { t -> combiner.invoke(t.t1.t1, t.t1.t2, t.t2) }
        )
    }

    override fun <I, I1, I2, I3, O> zip3(
        other1: DeferredContainer<FluxDeferredContainerWT, I1>,
        other2: DeferredContainer<FluxDeferredContainerWT, I2>,
        other3: DeferredContainer<FluxDeferredContainerWT, I3>,
        combiner: (I, I1, I2, I3) -> O,
        container: DeferredContainer<FluxDeferredContainerWT, I>
    ): DeferredContainer<FluxDeferredContainerWT, O> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container
                .narrowed()
                .flux
                .zipWith(other1.narrowed().flux)
                .zipWith(other2.narrowed().flux)
                .zipWith(other3.narrowed().flux)
                .map { t -> combiner.invoke(t.t1.t1.t1, t.t1.t1.t2, t.t1.t2, t.t2) }
        )
    }

    override fun <I> peek(
        ifSuccess: (I) -> Unit,
        ifFailure: (Throwable) -> Unit,
        container: DeferredContainer<FluxDeferredContainerWT, I>,
    ): DeferredContainer<FluxDeferredContainerWT, I> {
        return DeferredContainerFactory.FluxDeferredContainer(
            container
                .narrowed()
                .flux
                .doOnNext { i: I -> ifSuccess.invoke(i) }
                .doOnError { thr: Throwable -> ifFailure.invoke(thr) }
        )
    }
}
