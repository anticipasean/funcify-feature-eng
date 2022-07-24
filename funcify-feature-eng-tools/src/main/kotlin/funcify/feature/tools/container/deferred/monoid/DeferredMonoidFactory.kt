package funcify.feature.tools.container.deferred.monoid

import arrow.typeclasses.Monoid
import funcify.feature.tools.container.deferred.Deferred
import org.reactivestreams.Publisher

object DeferredMonoidFactory {

    private val homogeneousDeferredMonoid: Monoid<Deferred<Nothing>> =
        HomogeneousDeferredMonoid<Nothing>()

    fun <I> getHomogeneousDeferredMonoid(): Monoid<Deferred<I>> {
        @Suppress("UNCHECKED_CAST") //
        return homogeneousDeferredMonoid as Monoid<Deferred<I>>
    }

    fun <I, O> getHeterogeneousDeferredMonoid(
        initial: Deferred<I>,
        mapper: (I) -> O
    ): Monoid<Deferred<O>> {
        return HeterogeneousDeferredMonoid<I, O>(initial, mapper)
    }

    private class HomogeneousDeferredMonoid<I> : Monoid<Deferred<I>> {

        override fun empty(): Deferred<I> {
            return Deferred.empty()
        }

        override fun Deferred<I>.combine(b: Deferred<I>): Deferred<I> {
            /*
             * mergeWith contract in Java: `public final Flux<T> mergeWith(Publisher<? extends T>
             * other)` Kotlin translates publisher's covariant type parameter on the parameter type
             * into `out Nothing` as a result of its declaration-site variance setup
             */
            @Suppress("UNCHECKED_CAST") //
            return Deferred.fromFlux(this.toFlux().mergeWith(b as Publisher<out Nothing>))
        }
    }

    private class HeterogeneousDeferredMonoid<in I, O>(
        private val initial: Deferred<I>,
        private val mapper: (I) -> O
    ) : Monoid<Deferred<O>> {

        override fun empty(): Deferred<O> {
            return initial.map(mapper)
        }

        override fun Deferred<O>.combine(b: Deferred<O>): Deferred<O> {
            /*
             * mergeWith contract in Java: `public final Flux<T> mergeWith(Publisher<? extends T>
             * other)` Kotlin translates publisher's covariant type parameter on the parameter type
             * into `out Nothing` as a result of its declaration-site variance setup
             */
            @Suppress("UNCHECKED_CAST") //
            return Deferred.fromFlux(this.toFlux().mergeWith(b as Publisher<out Nothing>))
        }
    }
}
