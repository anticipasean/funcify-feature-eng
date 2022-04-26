package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import reactor.core.publisher.Mono

internal object DeferredSingleDesignFactory {

    data class KFutureSingleDesign<I>(val kFutureSingle: KFuture<I>) : DeferredDesign<I> {

        override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
            return template.fromKFuture(kFutureSingle)
        }
    }

    data class MonoSingleDesign<I>(val mono: Mono<out I>) : DeferredDesign<I> {

        override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I> {
            return template.fromMono(mono)
        }
    }

    fun <I> ofSingleDesign(kFuture: KFuture<I>): Deferred<I> {
        return KFutureSingleDesign<I>(kFuture)
    }

    fun <I> ofSingleDesign(mono: Mono<out I>): Deferred<I> {
        return MonoSingleDesign<I>(mono)
    }
}
