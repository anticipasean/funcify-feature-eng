package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredSingle.DeferredSingleWitness
import reactor.core.publisher.Mono

internal sealed interface DeferredSingle<out V> : DeferredContainer<DeferredSingleWitness, V> {

    enum class DeferredSingleWitness

    companion object {

        fun <V> of(kFuture: KFuture<V>): DeferredSingle<V> {
            return KFutureDeferredSingle(kFuture = kFuture)
        }

        fun <V> of(mono: Mono<out V>): DeferredSingle<V> {
            return MonoDeferredSingle(mono = mono)
        }

        data class KFutureDeferredSingle<out V>(val kFuture: KFuture<V>) : DeferredSingle<V> {
            override fun <R> fold(ifKFuture: (KFuture<V>) -> R, ifMono: (Mono<out V>) -> R): R {
                return ifKFuture.invoke(kFuture)
            }
        }

        data class MonoDeferredSingle<out V>(val mono: Mono<out V>) : DeferredSingle<V> {
            override fun <R> fold(ifKFuture: (KFuture<V>) -> R, ifMono: (Mono<out V>) -> R): R {
                return ifMono.invoke(mono)
            }
        }
    }

    fun <R> fold(ifKFuture: (KFuture<V>) -> R, ifMono: (Mono<out V>) -> R): R
}
