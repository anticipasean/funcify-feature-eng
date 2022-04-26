package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredMany.DeferredManyWitness
import reactor.core.publisher.Flux

internal interface DeferredMany<out V> : DeferredContainer<DeferredManyWitness, V> {

    enum class DeferredManyWitness

    companion object {

        fun <V> of(iterableKFuture: KFuture<Iterable<V>>): DeferredMany<V> {
            return IterableKFutureDeferredMany<V>(iterableKFuture = iterableKFuture)
        }

        fun <V> of(vFlux: Flux<V>): DeferredMany<V> {
            return FluxDeferredMany<V>(vFlux = vFlux)
        }

        data class IterableKFutureDeferredMany<V>(val iterableKFuture: KFuture<Iterable<V>>) :
            DeferredMany<V> {
            override fun <R> fold(
                ifKFuture: (KFuture<Iterable<V>>) -> R,
                ifFlux: (Flux<out V>) -> R
            ): R {
                return ifKFuture.invoke(iterableKFuture)
            }
        }

        data class FluxDeferredMany<V>(val vFlux: Flux<out V>) : DeferredMany<V> {
            override fun <R> fold(
                ifKFuture: (KFuture<Iterable<V>>) -> R,
                ifFlux: (Flux<out V>) -> R
            ): R {
                return ifFlux.invoke(vFlux)
            }
        }
    }

    fun <R> fold(ifKFuture: (KFuture<Iterable<V>>) -> R, ifFlux: (Flux<out V>) -> R): R
}
