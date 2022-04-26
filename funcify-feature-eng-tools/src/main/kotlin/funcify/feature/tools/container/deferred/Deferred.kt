package funcify.feature.tools.container.deferred

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PredicateExtensions.negate
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.stream.Stream
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.concurrent.Queues

interface Deferred<out V> : Iterable<V> {

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
    }

    fun <R> map(mapper: (V) -> R): Deferred<R>

    fun <R> flatMapCompletionStage(mapper: (V) -> CompletionStage<out R>): Deferred<R>

    fun <R> flatMapMono(mapper: (V) -> Mono<out R>): Deferred<R>

    fun <R> flatMapFlux(mapper: (V) -> Flux<out R>): Deferred<R>

    fun filter(condition: (V) -> Boolean, ifConditionNotMet: (V) -> Throwable): Deferred<V>

    fun filter(condition: (V) -> Boolean): Deferred<Option<V>>

    fun filterNot(condition: (V) -> Boolean): Deferred<Option<V>> {
        return filter(condition.negate<V>())
    }

    fun filterNot(condition: (V) -> Boolean, ifConditionMet: (V) -> Throwable): Deferred<V> {
        return filter(condition.negate<V>(), ifConditionMet)
    }

    /** Blocking method */
    override fun iterator(): Iterator<V>

    /** Blocking method */
    fun batchedIterator(batchSize: Int = Queues.SMALL_BUFFER_SIZE): Iterator<V>

    /** Blocking method */
    override fun spliterator(): Spliterator<@UnsafeVariance V>

    /** Blocking method */
    fun sequence(): Sequence<V> {
        return asSequence()
    }

    /** Blocking method */
    fun stream(bufferSize: Int = Queues.SMALL_BUFFER_SIZE): Stream<out V>

    fun blockForFirst(): Try<V>

    fun blockForLast(): Try<V>

    fun blockForAll(): Try<List<V>>

    fun toKFuture(): KFuture<V>

    fun toMono(): Mono<out V>

    fun toFlux(): Flux<out V>
}
