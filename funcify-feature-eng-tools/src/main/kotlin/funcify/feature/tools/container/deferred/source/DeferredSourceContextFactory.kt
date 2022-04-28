package funcify.feature.tools.container.deferred.source

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import funcify.feature.tools.container.deferred.template.DeferredTemplate
import funcify.feature.tools.container.deferred.template.ExecContextFluxDeferredTemplate
import funcify.feature.tools.container.deferred.template.ExecContextKFutureDeferredTemplate
import funcify.feature.tools.container.deferred.template.ExecContextMonoDeferredTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal object DeferredSourceContextFactory {

    internal val kFutureTemplate: ExecContextKFutureDeferredTemplate by lazy {
        object : ExecContextKFutureDeferredTemplate {}
    }
    internal val monoTemplate: ExecContextMonoDeferredTemplate by lazy {
        object : ExecContextMonoDeferredTemplate {}
    }
    internal val fluxTemplate: ExecContextFluxDeferredTemplate by lazy {
        object : ExecContextFluxDeferredTemplate {}
    }

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
