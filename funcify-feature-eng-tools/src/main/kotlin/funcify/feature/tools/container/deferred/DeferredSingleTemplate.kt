package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredSingle.DeferredSingleWitness
import java.util.concurrent.CompletionStage
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface DeferredSingleTemplate : DeferredTemplate<DeferredSingleWitness> {

    override fun <I> fromKFuture(kFuture: KFuture<I>): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(kFuture)
    }

    override fun <I : Iterable<T>, T> fromKFutureOfIterable(
        kFuture: KFuture<I>
    ): DeferredContainer<DeferredSingleWitness, T> {
        return DeferredSingle.of(kFuture.map { i: I -> i.first() })
    }

    override fun <I> fromCompletionStage(
        stage: CompletionStage<out I>
    ): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(KFuture.of(stage))
    }

    override fun <I> fromMono(mono: Mono<out I>): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(mono)
    }

    override fun <I> fromFlux(flux: Flux<out I>): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(flux.next())
    }

    override fun <I> fromPublisher(
        publisher: Publisher<out I>
    ): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(Mono.from(publisher))
    }

    override fun <I> fromSupplier(supplier: () -> I): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(Mono.fromSupplier(supplier))
    }

    override fun <I> fromIterable(
        iterable: Iterable<I>
    ): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(Flux.fromIterable(iterable).next())
    }

    override fun <I : Iterable<T>, T> fromCompletionStageOfIterable(
        stage: CompletionStage<out I>
    ): DeferredContainer<DeferredSingleWitness, T> {
        return DeferredSingle.of(
            Mono.fromCompletionStage(stage).flux().flatMap { i: I -> Flux.fromIterable(i) }.next()
        )
    }

    override fun <I> empty(): DeferredContainer<DeferredSingleWitness, I> {
        return DeferredSingle.of(Mono.empty())
    }

    override fun <I, O> map(
        container: DeferredContainer<DeferredSingleWitness, I>,
        mapper: (I) -> O
    ): DeferredContainer<DeferredSingleWitness, O> {
        return (container as DeferredSingle<I>).fold(
            { kFuture: KFuture<I> -> DeferredSingle.of(kFuture.map(mapper)) },
            { mono: Mono<out I> -> DeferredSingle.of(mono.map(mapper)) }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        container: DeferredContainer<DeferredSingleWitness, I>,
        mapper: (I) -> CompletionStage<out O>
    ): DeferredContainer<DeferredSingleWitness, O> {
        return (container as DeferredSingle<I>).fold(
            { kFuture: KFuture<I> -> fromKFuture(kFuture.flatMapCompletionStage(mapper)) },
            { mono: Mono<out I> ->
                fromMono(mono.flatMap { i: I -> Mono.fromCompletionStage(mapper.invoke(i)) })
            }
        )
    }
}
