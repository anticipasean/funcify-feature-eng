package funcify.feature.tools.container.deferred

import arrow.core.Option
import arrow.core.fold
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.monoid.DeferredMonoidFactory
import funcify.feature.tools.container.deferred.source.DeferredSourceContextFactory
import funcify.feature.tools.extensions.PredicateExtensions.negate
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.stream.Stream
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.util.concurrent.Queues

/**
 * Abstraction for uniformly linking and handling different asynchronous return types:
 * CompletableFuture<I>, Mono<I>, Flux<I>, Publisher<I>, etc. for both single value e.g.
 * CompletionStage<I> and multiple value e.g. Flux<I> containers. The actual container might need to
 * be used in cases where specialized features of that container e.g.
 * [reactor.core.publisher.Flux.groupBy(java.util.function.Function<? super T,? extends K>)] are
 * sought and can be retrieved through the transformer to*** methods e.g.
 * [funcify.feature.tools.container.deferred.Deferred.toFlux]
 */
interface Deferred<out I> : Iterable<I>, Publisher<@UnsafeVariance I> {

    companion object {
        @JvmStatic
        fun <I> fromKFuture(kFuture: KFuture<I>): Deferred<I> {
            return DeferredSourceContextFactory.KFutureDeferredSourceContext(kFuture)
        }
        @JvmStatic
        fun <I : Iterable<T>, T> fromKFutureOfIterable(kFuture: KFuture<I>): Deferred<T> {
            return DeferredSourceContextFactory.FluxDeferredSourceContext(
                Mono.fromCompletionStage(kFuture.fold { cs, _ -> cs }).flatMapIterable { i -> i }
            )
        }
        @JvmStatic
        fun <I> fromCompletionStage(stage: CompletionStage<out I>): Deferred<I> {
            return fromKFuture(KFuture.of(stage))
        }
        @JvmStatic
        fun <I> fromMono(mono: Mono<out I>): Deferred<I> {
            return DeferredSourceContextFactory.MonoDeferredSourceContext(mono)
        }
        @JvmStatic
        fun <I> fromFlux(flux: Flux<out I>): Deferred<I> {
            return DeferredSourceContextFactory.FluxDeferredSourceContext(flux)
        }
        @JvmStatic
        fun <I> fromPublisher(publisher: Publisher<out I>): Deferred<I> {
            return when (publisher) {
                is Deferred -> publisher
                is Mono -> fromMono(publisher)
                is Flux -> fromFlux(publisher)
                else -> DeferredSourceContextFactory.FluxDeferredSourceContext(Flux.from(publisher))
            }
        }
        @JvmStatic
        fun <I> fromSupplier(supplier: () -> I): Deferred<I> {
            return DeferredSourceContextFactory.KFutureDeferredSourceContext(
                KFuture.defer(supplier)
            )
        }
        @JvmStatic
        fun <I> fromIterable(iterable: Iterable<I>): Deferred<I> {
            return DeferredSourceContextFactory.FluxDeferredSourceContext(
                Flux.fromIterable(iterable)
            )
        }
        @JvmStatic
        fun <I : Iterable<T>, T> fromCompletionStageOfIterable(
            stage: CompletionStage<out I>
        ): Deferred<T> {
            return fromKFutureOfIterable(KFuture.of(stage))
        }

        @JvmStatic
        fun <I> empty(): Deferred<I> {
            return fromFlux(Flux.empty())
        }

        @JvmStatic
        fun <I> completed(input: I): Deferred<I> {
            return fromKFuture(KFuture.completed(input))
        }

        @JvmStatic
        fun <I> failed(error: Throwable): Deferred<I> {
            return fromKFuture(KFuture.failed(error))
        }

        @JvmStatic
        fun <I> fromAttempt(attempt: Try<I>): Deferred<I> {
            return fromKFuture(KFuture.fromAttempt(attempt))
        }

        @JvmStatic
        fun <S, D, I> deferredSequence(sequenceOfDeferred: S): Deferred<I> where
        S : Sequence<D>,
        D : Deferred<I> {
            return sequenceOfDeferred.fold(DeferredMonoidFactory.getHomogeneousDeferredMonoid<I>())
        }

        @JvmStatic
        fun <S, D, I> deferredStream(streamOfDeferred: S): Deferred<I> where
        S : Stream<out D>,
        D : Deferred<I> {
            return fromFlux(
                streamOfDeferred.reduce(
                    Flux.empty<I>(),
                    { f, d -> f.mergeWith(d) },
                    { f1, f2 -> f1.mergeWith(f2) }
                )
            )
        }

        @JvmStatic
        fun <C, D, I> deferredIterable(iterableOfDeferred: C): Deferred<I> where
        C : Iterable<D>,
        D : Deferred<I> {
            return iterableOfDeferred.fold(DeferredMonoidFactory.getHomogeneousDeferredMonoid<I>())
        }

        fun <O, I> Deferred<O>.flattenOptions(): Deferred<I> where O : Option<I> {
            return this.flatMapMono { option: O ->
                option.fold({ Mono.empty() }, { i -> Mono.just(i) })
            }
        }
    }

    fun <O> map(mapper: (I) -> O): Deferred<O>

    fun <O> map(executor: Executor, mapper: (I) -> O): Deferred<O>

    fun <O> map(scheduler: Scheduler, mapper: (I) -> O): Deferred<O>

    fun mapFailure(mapper: (Throwable) -> Throwable): Deferred<I> {
        return flatMapFailure { thr -> Deferred.failed(mapper.invoke(thr)) }
    }

    fun <O> flatMap(mapper: (I) -> Deferred<O>): Deferred<O>

    fun <O> flatMap(executor: Executor, mapper: (I) -> Deferred<O>): Deferred<O>

    fun <O> flatMap(scheduler: Scheduler, mapper: (I) -> Deferred<O>): Deferred<O>

    fun <O> flatMapKFuture(mapper: (I) -> KFuture<O>): Deferred<O>

    fun <O> flatMapKFuture(executor: Executor, mapper: (I) -> KFuture<O>): Deferred<O>

    fun <O> flatMapKFuture(scheduler: Scheduler, mapper: (I) -> KFuture<O>): Deferred<O>

    fun <O> flatMapCompletionStage(mapper: (I) -> CompletionStage<out O>): Deferred<O>

    fun <O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>
    ): Deferred<O>

    fun <O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>
    ): Deferred<O>

    fun <O> flatMapMono(mapper: (I) -> Mono<out O>): Deferred<O>

    fun <O> flatMapMono(executor: Executor, mapper: (I) -> Mono<out O>): Deferred<O>

    fun <O> flatMapMono(scheduler: Scheduler, mapper: (I) -> Mono<out O>): Deferred<O>

    fun <O> flatMapFlux(mapper: (I) -> Flux<out O>): Deferred<O>

    fun <O> flatMapFlux(executor: Executor, mapper: (I) -> Flux<out O>): Deferred<O>

    fun <O> flatMapFlux(scheduler: Scheduler, mapper: (I) -> Flux<out O>): Deferred<O>

    fun <O> flatMapIterable(mapper: (I) -> Iterable<O>): Deferred<O>

    fun flatMapFailure(mapper: (Throwable) -> Deferred<@UnsafeVariance I>): Deferred<I>

    fun recoverFromFailure(mapper: (Throwable) -> Deferred<@UnsafeVariance I>): Deferred<I> {
        return flatMapFailure(mapper)
    }

    fun <I1, O> zip(other: Deferred<I1>, combiner: (I, I1) -> O): Deferred<O>

    fun <I1, I2, O> zip2(
        other1: Deferred<I1>,
        other2: Deferred<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O>

    fun <I1, I2, I3, O> zip3(
        other1: Deferred<I1>,
        other2: Deferred<I2>,
        other3: Deferred<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O>

    fun <I1, O> zip(other: KFuture<I1>, combiner: (I, I1) -> O): Deferred<O>

    fun <I1, I2, O> zip2(
        other1: KFuture<I1>,
        other2: KFuture<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O>

    fun <I1, I2, I3, O> zip3(
        other1: KFuture<I1>,
        other2: KFuture<I2>,
        other3: KFuture<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O>

    fun <I1, O> zip(other: Mono<I1>, combiner: (I, I1) -> O): Deferred<O>

    fun <I1, I2, O> zip2(
        other1: Mono<I1>,
        other2: Mono<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O>

    fun <I1, I2, I3, O> zip3(
        other1: Mono<I1>,
        other2: Mono<I2>,
        other3: Mono<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O>

    fun <I1, O> zip(other: Flux<I1>, combiner: (I, I1) -> O): Deferred<O>

    fun <I1, I2, O> zip2(
        other1: Flux<I1>,
        other2: Flux<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O>

    fun <I1, I2, I3, O> zip3(
        other1: Flux<I1>,
        other2: Flux<I2>,
        other3: Flux<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O>

    fun filter(condition: (I) -> Boolean, ifConditionNotMet: (I) -> Throwable): Deferred<I>

    fun filter(condition: (I) -> Boolean): Deferred<Option<I>>

    fun filterNot(condition: (I) -> Boolean): Deferred<Option<I>> {
        return filter(condition.negate<I>())
    }

    fun filterNot(condition: (I) -> Boolean, ifConditionMet: (I) -> Throwable): Deferred<I> {
        return filter(condition.negate<I>(), ifConditionMet)
    }

    fun peek(ifSuccess: (I) -> Unit, ifFailure: (Throwable) -> Unit): Deferred<I>

    fun <O> ap(other: Deferred<(I) -> O>): Deferred<O> {
        return flatMap { i: I -> other.map { func: (I) -> O -> func.invoke(i) } }
    }

    /** Blocking method */
    override fun iterator(): Iterator<I>

    /** Blocking method */
    fun batchedIterator(batchSize: Int = Queues.SMALL_BUFFER_SIZE): Iterator<I>

    /** Blocking method */
    override fun spliterator(): Spliterator<@UnsafeVariance I>

    /** Blocking method */
    fun sequence(): Sequence<I> {
        return asSequence()
    }

    /** Blocking method */
    fun stream(bufferSize: Int = Queues.SMALL_BUFFER_SIZE): Stream<out I>

    fun blockForFirst(): Try<I>

    fun blockForLast(): Try<I>

    fun blockForAll(): Try<List<I>>

    fun toCompletionStage(): CompletionStage<out List<I>> {
        return toKFuture().fold { cs: CompletionStage<out List<I>>, _: Option<Executor> -> cs }
    }

    fun toKFuture(): KFuture<List<I>>

    fun toMono(): Mono<out List<I>>

    fun toFlux(): Flux<out I>

    override fun subscribe(s: Subscriber<in I>?)
}
