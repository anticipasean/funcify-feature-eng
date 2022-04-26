package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredMany.DeferredManyWitness
import java.util.concurrent.CompletionStage
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface DeferredManyTemplate : DeferredTemplate<DeferredManyWitness> {

    override fun <I> fromKFuture(kFuture: KFuture<I>): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(kFuture.map { i: I -> persistentListOf(i) })
    }

    override fun <I : Iterable<T>, T> fromKFutureOfIterable(
        kFuture: KFuture<I>
    ): DeferredContainer<DeferredManyWitness, T> {
        return DeferredMany.of(kFuture)
    }

    override fun <I> fromCompletionStage(
        stage: CompletionStage<out I>
    ): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(KFuture.of(stage).map { i: I -> persistentListOf(i) })
    }

    override fun <I> fromMono(mono: Mono<out I>): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(mono.flux())
    }

    override fun <I> fromFlux(flux: Flux<out I>): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(flux)
    }

    override fun <I> fromPublisher(
        publisher: Publisher<out I>
    ): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(Flux.from(publisher))
    }

    override fun <I> fromSupplier(supplier: () -> I): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(Mono.fromSupplier(supplier).flux())
    }

    override fun <I> fromIterable(
        iterable: Iterable<I>
    ): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(Flux.fromIterable(iterable))
    }

    override fun <I : Iterable<T>, T> fromCompletionStageOfIterable(
        stage: CompletionStage<out I>
    ): DeferredContainer<DeferredManyWitness, T> {
        return DeferredMany.of(KFuture.of(stage))
    }

    override fun <I> empty(): DeferredContainer<DeferredManyWitness, I> {
        return DeferredMany.of(vFlux = Flux.empty())
    }

    override fun <I, O> map(
        container: DeferredContainer<DeferredManyWitness, I>,
        mapper: (I) -> O
    ): DeferredContainer<DeferredManyWitness, O> {
        return (container as DeferredMany<I>).fold(
            { iterableKFuture: KFuture<Iterable<I>> ->
                DeferredMany.of(
                    iterableKFuture.map { iIter: Iterable<I> ->
                        when (iIter) {
                            is PersistentList ->
                                iIter.fold(persistentListOf()) { acc: PersistentList<O>, i: I ->
                                    acc.add(mapper.invoke(i))
                                }
                            is PersistentSet ->
                                iIter.fold(persistentSetOf()) { acc: PersistentSet<O>, i: I ->
                                    acc.add(mapper.invoke(i))
                                }
                            else -> iIter.map(mapper)
                        }
                    }
                )
            },
            { iFlux: Flux<out I> -> DeferredMany.of(iFlux.map(mapper)) }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        container: DeferredContainer<DeferredManyWitness, I>,
        mapper: (I) -> CompletionStage<out O>
    ): DeferredContainer<DeferredManyWitness, O> {
        return (container as DeferredMany<I>).fold(
            { kFuture: KFuture<Iterable<I>> ->
                fromKFutureOfIterable(
                    kFuture
                        .map { iter: Iterable<I> ->
                            iter.fold(persistentListOf()) { acc: PersistentList<KFuture<O>>, i: I ->
                                acc.add(KFuture.of(mapper.invoke(i)))
                            }
                        }
                        .flatMap { pListKFutures: PersistentList<KFuture<O>> ->
                            KFuture.combineIterableOf(pListKFutures)
                        }
                )
            },
            { flux: Flux<out I> ->
                fromFlux(flux.flatMap { i: I -> Mono.fromCompletionStage(mapper.invoke(i)) })
            }
        )
    }
}
