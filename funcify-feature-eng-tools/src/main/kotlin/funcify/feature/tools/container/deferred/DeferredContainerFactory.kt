package funcify.feature.tools.container.deferred

import arrow.core.Option
import arrow.core.none
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

internal object DeferredContainerFactory {

    fun <V> DeferredContainer<KFutureDeferredContainerWT, V>.narrowed():
        KFutureDeferredContainer<V> {
        return KFutureDeferredContainer.narrow(this)
    }

    fun <V> DeferredContainer<MonoDeferredContainerWT, V>.narrowed(): MonoDeferredContainer<V> {
        return MonoDeferredContainer.narrow(this)
    }
    fun <V> DeferredContainer<FluxDeferredContainerWT, V>.narrowed(): FluxDeferredContainer<V> {
        return FluxDeferredContainer.narrow(this)
    }

    internal class KFutureDeferredContainer<out V>(val kFuture: KFuture<V>) :
        DeferredContainer<KFutureDeferredContainerWT, V> {
        companion object {
            enum class KFutureDeferredContainerWT

            @JvmStatic
            fun <V> narrow(
                container: DeferredContainer<KFutureDeferredContainerWT, V>
            ): KFutureDeferredContainer<V> {
                return container as KFutureDeferredContainer<V>
            }
            @JvmStatic
            fun <V> widen(
                container: KFutureDeferredContainer<V>
            ): DeferredContainer<KFutureDeferredContainerWT, V> {
                return container
            }
        }
    }

    internal class MonoDeferredContainer<out V>(
        val mono: Mono<out V>,
        val schedulerOpt: Option<Scheduler> = none<Scheduler>()
    ) : DeferredContainer<MonoDeferredContainerWT, V> {
        companion object {
            enum class MonoDeferredContainerWT

            @JvmStatic
            fun <V> narrow(
                container: DeferredContainer<MonoDeferredContainerWT, V>
            ): MonoDeferredContainer<V> {
                return container as MonoDeferredContainer<V>
            }

            @JvmStatic
            fun <V> widen(
                container: MonoDeferredContainer<V>
            ): DeferredContainer<MonoDeferredContainerWT, V> {
                return container
            }
        }
    }

    internal class FluxDeferredContainer<out V>(
        val flux: Flux<out V>,
        val schedulerOpt: Option<Scheduler> = none<Scheduler>()
    ) : DeferredContainer<FluxDeferredContainerWT, V> {
        companion object {

            enum class FluxDeferredContainerWT

            @JvmStatic
            fun <V> narrow(
                container: DeferredContainer<FluxDeferredContainerWT, V>
            ): FluxDeferredContainer<V> {
                return container as FluxDeferredContainer<V>
            }

            @JvmStatic
            fun <V> widen(
                container: FluxDeferredContainer<V>
            ): DeferredContainer<FluxDeferredContainerWT, V> {
                return container
            }
        }
    }
}
