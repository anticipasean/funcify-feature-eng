package funcify.feature.tools.container.deferred.template

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
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

    fun <I, O> flatMapKFuture(
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<WT, I>
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

    fun <I, I1, O> zip1(
        other: DeferredContainer<WT, I1>,
        combiner: (I, I1) -> O,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, I1, I2, O> zip2(
        other1: DeferredContainer<WT, I1>,
        other2: DeferredContainer<WT, I2>,
        combiner: (I, I1, I2) -> O,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>

    fun <I, I1, I2, I3, O> zip3(
        other1: DeferredContainer<WT, I1>,
        other2: DeferredContainer<WT, I2>,
        other3: DeferredContainer<WT, I3>,
        combiner: (I, I1, I2, I3) -> O,
        container: DeferredContainer<WT, I>
    ): DeferredContainer<WT, O>
}
