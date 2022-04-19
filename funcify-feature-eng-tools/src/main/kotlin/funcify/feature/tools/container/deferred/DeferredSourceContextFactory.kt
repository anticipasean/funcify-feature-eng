package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal object DeferredSourceContextFactory {

    private val kFutureTemplate: KFutureDeferredTemplate = object : KFutureDeferredTemplate {}
    private val monoTemplate: MonoDeferredTemplate = object : MonoDeferredTemplate {}
    private val fluxTemplate: FluxDeferredTemplate = object : FluxDeferredTemplate {}
    internal class KFutureDeferredSourceContext<V>(
        val kFuture: KFuture<V>,
        override val template: DeferredTemplate<KFutureDeferredContainerWT> = kFutureTemplate
    ) : DeferredSourceContext<KFutureDeferredContainerWT, V> {
        override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, V> {
            return template.fromKFuture(kFuture)
        }
    }

    internal class MonoDeferredSourceContext<V>(
        val mono: Mono<V>,
        override val template: DeferredTemplate<MonoDeferredContainerWT> = monoTemplate
    ) : DeferredSourceContext<MonoDeferredContainerWT, V> {
        override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, V> {
            return template.fromMono(mono)
        }
    }

    internal class FluxDeferredSourceContext<V>(
        val flux: Flux<V>,
        override val template: DeferredTemplate<FluxDeferredContainerWT> = fluxTemplate
    ) : DeferredSourceContext<FluxDeferredContainerWT, V> {
        override fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, V> {
            return template.fromFlux(flux)
        }
    }
}
