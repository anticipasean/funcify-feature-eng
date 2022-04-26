package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import java.util.concurrent.CompletionStage
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface DeferredTemplate<WT> {

    fun <I> fromKFuture(kFuture: KFuture<I>): DeferredContainer<WT, I>

    fun <I : Iterable<T>, T> fromKFutureOfIterable(kFuture: KFuture<I>): DeferredContainer<WT, T>

    fun <I> fromCompletionStage(stage: CompletionStage<out I>): DeferredContainer<WT, I>

    fun <I> fromMono(mono: Mono<out I>): DeferredContainer<WT, I>

    fun <I> fromFlux(flux: Flux<out I>): DeferredContainer<WT, I>

    fun <I> fromPublisher(publisher: Publisher<out I>): DeferredContainer<WT, I>

    fun <I> fromSupplier(supplier: () -> I): DeferredContainer<WT, I>

    fun <I> fromIterable(iterable: Iterable<I>): DeferredContainer<WT, I>

    fun <I : Iterable<T>, T> fromCompletionStageOfIterable(
        stage: CompletionStage<out I>
    ): DeferredContainer<WT, T>

    fun <I> empty(): DeferredContainer<WT, I>

    fun <I, O> map(container: DeferredContainer<WT, I>, mapper: (I) -> O): DeferredContainer<WT, O>

    fun <I, O> flatMapCompletionStage(
        container: DeferredContainer<WT, I>,
        mapper: (I) -> CompletionStage<out O>
    ): DeferredContainer<WT, O>
}
